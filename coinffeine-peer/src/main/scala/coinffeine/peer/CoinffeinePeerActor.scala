package coinffeine.peer

import scala.concurrent.duration._
import scala.util.control.NonFatal

import akka.actor.{Address => _, _}
import akka.pattern._
import akka.util.Timeout

import coinffeine.common.akka._
import coinffeine.model.bitcoin.{Address, ImmutableTransaction, NetworkComponent}
import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.market.{Order, OrderId}
import coinffeine.model.network.MutableCoinffeineNetworkProperties
import coinffeine.peer.alarms.AlarmReporterActor
import coinffeine.peer.amounts.AmountsComponent
import coinffeine.peer.bitcoin.BitcoinPeerActor
import coinffeine.peer.bitcoin.wallet.WalletActor
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.market._
import coinffeine.peer.market.orders.{OrderActor, OrderSupervisor}
import coinffeine.peer.market.submission.SubmissionSupervisor
import coinffeine.peer.payment.MutablePaymentProcessorProperties
import coinffeine.peer.payment.PaymentProcessorActor.RetrieveBalance
import coinffeine.peer.payment.okpay.OkPayProcessorActor
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.messages.brokerage
import coinffeine.protocol.messages.brokerage.{OpenOrdersRequest, QuoteRequest}

/** Implementation of the topmost actor on a peer node. It starts all the relevant actors like
  * the peer actor and the message gateway and supervise them.
  */
class CoinffeinePeerActor(props: CoinffeinePeerActor.PropsCatalogue, serviceTimeout: FiniteDuration)
  extends Actor with ActorLogging with ServiceLifecycle[Unit] {

  import CoinffeinePeerActor._
  import context.dispatcher

  private val alarmReporterRef = context.actorOf(props.alarmReporter, "alarmReporter")
  private val gatewayRef = context.actorOf(props.gateway(context.system), "gateway")
  private val paymentProcessorRef = context.actorOf(props.paymentProcessor, "paymentProcessor")
  private val bitcoinPeerRef = context.actorOf(props.bitcoinPeer, "bitcoinPeer")
  private val marketInfoRef = context.actorOf(props.marketInfo(gatewayRef), "marketInfo")
  private var orderSupervisorRef: ActorRef = _
  private var walletRef: ActorRef = _

  override def onStart(arg: Unit) = {
    implicit val timeout = Timeout(serviceTimeout)
    log.info("Starting Coinffeine peer actor...")
    (for {
      _ <- Service.askStart(alarmReporterRef)
      _ <- Service.askStart(paymentProcessorRef)
      _ <- Service.askStart(bitcoinPeerRef)
      _ <- Service.askStart(gatewayRef)
      walletActorRef <- AskPattern(bitcoinPeerRef, BitcoinPeerActor.RetrieveWalletActor)
        .withReply[BitcoinPeerActor.WalletActorRef]
      blockchainActorRef <- AskPattern(bitcoinPeerRef, BitcoinPeerActor.RetrieveBlockchainActor)
        .withReply[BitcoinPeerActor.BlockchainActorRef]
    } yield (walletActorRef, blockchainActorRef)).pipeTo(self)

    BecomeStarting {
      case (BitcoinPeerActor.WalletActorRef(retrievedWalletRef),
            BitcoinPeerActor.BlockchainActorRef(retrievedBlockchainRef)) =>
        walletRef = retrievedWalletRef
        val collaborators = OrderSupervisorCollaborators(
          gatewayRef, paymentProcessorRef, bitcoinPeerRef, retrievedBlockchainRef, walletRef)
        orderSupervisorRef = context.actorOf(props.orderSupervisor(collaborators), "orders")
        completeStart(handleMessages)
        log.info("Coinffeine peer actor successfully started!")
      case Status.Failure(cause) =>
        log.error(cause, "Coinffeine peer actor failed to start")
        cancelStart(cause)
    }
  }

  override protected def onStop() = {
    implicit val timeout = Timeout(serviceTimeout)
    log.info("Stopping Coinffeine peer")
    Service
      .askStopAll(paymentProcessorRef, bitcoinPeerRef, gatewayRef, alarmReporterRef)
      .pipeTo(self)
    BecomeStopping {
      case () =>
        completeStop()
        log.info("Stopped Coinffeine peer")
      case Status.Failure(cause) => cancelStop(cause)
    }
  }

  private val handleMessages: Receive = {
    case message @ (OpenOrder(_) | CancelOrder(_)) =>
      orderSupervisorRef forward message
    case message @ WithdrawWalletFunds(amount, to) =>
      implicit val txBroadcastTimeout = new Timeout(WithdrawFundsTxBroadcastTimeout)
      (for {
        txCreated <- AskPattern(walletRef, WalletActor.CreateTransaction(amount, to))
          .withImmediateReply[WalletActor.TransactionCreated]
        txPublished <- AskPattern(bitcoinPeerRef, BitcoinPeerActor.PublishTransaction(txCreated.tx))
          .withReply[BitcoinPeerActor.TransactionPublished]()
      } yield txPublished.broadcastTx)
        .map(tx => WalletFundsWithdrawn(amount, to, tx))
        .recover { case NonFatal(ex) => WalletFundsWithdrawFailure(amount, to, ex) }
        .pipeTo(sender())

    case message @ RetrieveBalance(_) =>
      paymentProcessorRef forward message

    case QuoteRequest(market) =>
      marketInfoRef.forward(MarketInfoActor.RequestQuote(market))
    case OpenOrdersRequest(market) =>
      marketInfoRef.forward(MarketInfoActor.RequestOpenOrders(market))
  }
}

/** Topmost actor on a peer node. */
object CoinffeinePeerActor {

  val WithdrawFundsTxBroadcastTimeout = 30.seconds

  /** Open a new order.
    *
    * Note that, in case of having a previous order at the same price, this means an increment
    * of its amount.
    *
    * @param order Order to open
    */
  case class OpenOrder(order: Order[_ <: FiatCurrency])

  /** Cancel an order
    *
    * Note that this can cancel partially an existing order for a greater amount of bitcoin.
    *
    * @param order  Order to cancel
    */
  case class CancelOrder(order: OrderId)

  /** Ask for the currently open orders. To be replied with an [[brokerage.OpenOrders]]. */
  type RetrieveMarketOrders = brokerage.OpenOrdersRequest

  /** A request to withdraw funds from Bitcoin wallet. */
  case class WithdrawWalletFunds(amount: Bitcoin.Amount, to: Address)

  /** A response reporting a successful withdraw of wallet funds. */
  case class WalletFundsWithdrawn(amount: Bitcoin.Amount,
                                  to: Address,
                                  tx: ImmutableTransaction)

  /** A response reporting a failure while withdrawing wallet funds. */
  case class WalletFundsWithdrawFailure(amount: Bitcoin.Amount,
                                        to: Address,
                                        failure: Throwable)

  case class OrderSupervisorCollaborators(gateway: ActorRef,
                                          paymentProcessor: ActorRef,
                                          bitcoinPeer: ActorRef,
                                          blockchain: ActorRef,
                                          wallet: ActorRef)

  case class PropsCatalogue(alarmReporter: Props,
                            gateway: ActorSystem => Props,
                            marketInfo: ActorRef => Props,
                            orderSupervisor: OrderSupervisorCollaborators => Props,
                            bitcoinPeer: Props,
                            paymentProcessor: Props)

  trait Component { this: MessageGateway.Component
    with AlarmReporterActor.Component
    with BitcoinPeerActor.Component
    with ExchangeActor.Component
    with ConfigComponent
    with NetworkComponent
    with ProtocolConstants.Component
    with MutablePaymentProcessorProperties.Component
    with MutableCoinffeineNetworkProperties.Component
    with AmountsComponent =>

    lazy val peerProps: Props = {
      val props = PropsCatalogue(
        alarmReporterProps,
        messageGatewayProps(configProvider.messageGatewaySettings(),
          configProvider.relaySettings().clientSettings),
        MarketInfoActor.props,
        orderSupervisorProps,
        bitcoinPeerProps,
        OkPayProcessorActor.props(configProvider.okPaySettings, paymentProcessorProperties)
      )
      Props(new CoinffeinePeerActor(props, configProvider.generalSettings().serviceStartStopTimeout))
    }

    private def orderSupervisorProps(orderSupervisorCollaborators: OrderSupervisorCollaborators) =
      OrderSupervisor.props(new OrderSupervisor.Delegates {
        import orderSupervisorCollaborators._

        override def orderActorProps(order: Order[_ <: FiatCurrency], submission: ActorRef) = {
          val collaborators = OrderActor.Collaborators(wallet, paymentProcessor, submission,
            gateway, bitcoinPeer, blockchain)
          OrderActor.props(exchangeActorProps, network, amountsCalculator, order,
            coinffeineNetworkProperties, collaborators, configProvider.messageGatewaySettings().peerId)
        }

        override val submissionProps = SubmissionSupervisor.props(gateway, protocolConstants)
      })
  }
}
