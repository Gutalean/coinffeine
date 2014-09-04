package coinffeine.peer

import scala.concurrent.duration._

import akka.actor._
import akka.pattern._
import akka.util.Timeout

import coinffeine.common.akka.{AskPattern, ServiceActor, ServiceRegistry, ServiceRegistryActor}
import coinffeine.model.bitcoin.NetworkComponent
import coinffeine.model.currency.{BitcoinAmount, FiatCurrency}
import coinffeine.model.event.{BitcoinConnectionStatus, CoinffeineConnectionStatus}
import coinffeine.model.market.{Order, OrderId}
import coinffeine.peer.amounts.AmountsComponent
import coinffeine.peer.bitcoin.BitcoinPeerActor
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.market.{MarketInfoActor, OrderSupervisor}
import coinffeine.peer.payment.PaymentProcessorActor.RetrieveBalance
import coinffeine.peer.payment.okpay.OkPayProcessorActor
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.BrokerAddress
import coinffeine.protocol.messages.brokerage
import coinffeine.protocol.messages.brokerage.{OpenOrdersRequest, QuoteRequest}

/** Implementation of the topmost actor on a peer node. It starts all the relevant actors like
  * the peer actor and the message gateway and supervise them.
  */
class CoinffeinePeerActor(
    listenPort: Int,
    brokerAddress: BrokerAddress,
    props: CoinffeinePeerActor.PropsCatalogue) extends Actor with ActorLogging with ServiceActor[Unit] {
  import context.dispatcher

import coinffeine.peer.CoinffeinePeerActor._

  private val registryRef = context.actorOf(ServiceRegistryActor.props(), "registry")
  private val registry = new ServiceRegistry(registryRef)

  private val gatewayRef = context.actorOf(props.gateway, "gateway")

  registry.register(MessageGateway.ServiceId, gatewayRef)

  private val paymentProcessorRef = context.actorOf(props.paymentProcessor, "paymentProcessor")
  private val bitcoinPeerRef = context.actorOf(props.bitcoinPeer, "bitcoinPeer")
  private val marketInfoRef = context.actorOf(props.marketInfo(gatewayRef), "marketInfo")
  private val orderSupervisorRef = context.actorOf(props.orderSupervisor, "orders")
  private var walletRef: ActorRef = _

  override def starting(args: Unit) = {
    implicit val timeout = Timeout(ServiceStartStopTimeout)
    log.info("Starting Coinffeine peer actor...")
    // TODO: replace all children actors by services and start them here
    (for {
      _ <- ServiceActor.askStart(paymentProcessorRef)
      _ <- ServiceActor.askStart(bitcoinPeerRef)
      _ <- ServiceActor.askStart(gatewayRef, MessageGateway.JoinAsPeer(listenPort, brokerAddress))
      walletActorRef <- AskPattern(bitcoinPeerRef, BitcoinPeerActor.RetrieveWalletActor)
        .withReply[BitcoinPeerActor.WalletActorRef]
    } yield walletActorRef).pipeTo(self)

    handle {
      case BitcoinPeerActor.WalletActorRef(retrievedWalletRef) =>
        walletRef = retrievedWalletRef
        orderSupervisorRef !
          OrderSupervisor.Initialize(registryRef, paymentProcessorRef, bitcoinPeerRef, walletRef)
        becomeStarted(handleMessages)
        log.info("Coinffeine peer actor successfully started!")
      case Status.Failure(cause) =>
        log.error(cause, "Coinffeine peer actor failed to start")
        cancelStart(cause)
    }
  }

  override protected def stopping(): Receive = {
    implicit val timeout = Timeout(ServiceStartStopTimeout)
    ServiceActor.askStopAll(paymentProcessorRef, bitcoinPeerRef, gatewayRef).pipeTo(self)
    handle {
      case () => becomeStopped()
      case Status.Failure(cause) => cancelStop(cause)
    }
  }

  private val handleMessages: Receive = {
    case message @ (OpenOrder(_) | CancelOrder(_, _) | RetrieveOpenOrders) =>
      orderSupervisorRef forward message
    case message @ RetrieveWalletBalance =>
      walletRef forward message
    case message @ RetrieveBalance(_) =>
      paymentProcessorRef forward message

    case QuoteRequest(market) =>
      marketInfoRef.forward(MarketInfoActor.RequestQuote(market))
    case OpenOrdersRequest(market) =>
      marketInfoRef.forward(MarketInfoActor.RequestOpenOrders(market))

    case RetrieveConnectionStatus =>
      (for {
        bitcoinStatus <- AskPattern(bitcoinPeerRef, BitcoinPeerActor.RetrieveConnectionStatus)
          .withImmediateReply[BitcoinConnectionStatus]()
        coinffeineStatus <- AskPattern(gatewayRef, MessageGateway.RetrieveConnectionStatus)
          .withImmediateReply[CoinffeineConnectionStatus]()
      } yield ConnectionStatus(bitcoinStatus, coinffeineStatus)).pipeTo(sender())
  }
}

/** Topmost actor on a peer node. */
object CoinffeinePeerActor {

  val ServiceStartStopTimeout = 10.seconds

  /** Message sent to the peer to get a [[ConnectionStatus]] in response */
  case object RetrieveConnectionStatus
  case class ConnectionStatus(bitcoinStatus: BitcoinConnectionStatus,
                              coinffeineStatus: CoinffeineConnectionStatus) {
    def connected: Boolean = bitcoinStatus.connected && coinffeineStatus.connected
  }

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
    * @param reason A user friendly description of why the order is cancelled
    */
  case class CancelOrder(order: OrderId, reason: String)

  /** Ask for own orders opened in any market. */
  case object RetrieveOpenOrders

  /** Reply to [[RetrieveOpenOrders]] message. */
  case class RetrievedOpenOrders(orders: Seq[Order[_ <: FiatCurrency]])

  /** Ask for the currently open orders. To be replied with an [[brokerage.OpenOrders]]. */
  type RetrieveMarketOrders = brokerage.OpenOrdersRequest

  /** Ask for the current wallet balance */
  case object RetrieveWalletBalance

  /** Response for [[RetrieveWalletBalance]] */
  case class WalletBalance(amount: BitcoinAmount)

  private val PortSetting = "coinffeine.peer.port"
  private val BrokerHostnameSetting = "coinffeine.broker.hostname"
  private val BrokerPortSetting = "coinffeine.broker.port"

  case class PropsCatalogue(gateway: Props,
                            marketInfo: ActorRef => Props,
                            orderSupervisor: Props,
                            bitcoinPeer: Props,
                            paymentProcessor: Props)

  trait Component { this: MessageGateway.Component
    with BitcoinPeerActor.Component
    with ExchangeActor.Component
    with ConfigComponent
    with NetworkComponent
    with ProtocolConstants.Component
    with AmountsComponent =>

    lazy val peerProps: Props = {
      val ownPort = configProvider.messageGatewaySettings.peerPort
      val brokerHostname = configProvider.messageGatewaySettings.brokerHost
      val brokerPort= configProvider.messageGatewaySettings.brokerPort
      val props = PropsCatalogue(
        messageGatewayProps(configProvider.messageGatewaySettings),
        MarketInfoActor.props,
        OrderSupervisor.props(
          exchangeActorProps, network, protocolConstants, exchangeAmountsCalculator),
        bitcoinPeerProps,
        OkPayProcessorActor.props(configProvider.okPaySettings)
      )
      Props(new CoinffeinePeerActor(ownPort, BrokerAddress(brokerHostname, brokerPort), props))
    }
  }
}
