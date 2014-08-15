package coinffeine.peer

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import akka.actor._
import akka.pattern._
import akka.util.Timeout

import coinffeine.common.akka.AskPattern
import coinffeine.model.bitcoin.NetworkComponent
import coinffeine.model.currency.{BitcoinAmount, FiatCurrency}
import coinffeine.model.market.{Order, OrderId}
import coinffeine.model.network.PeerId
import coinffeine.peer.api.event.{BitcoinConnectionStatus, CoinffeineConnectionStatus}
import coinffeine.peer.bitcoin.BitcoinPeerActor
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.market.{MarketInfoActor, OrderSupervisor}
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.peer.payment.PaymentProcessorActor.RetrieveBalance
import coinffeine.peer.payment.okpay.OkPayProcessorActor
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.{BrokerAddress, JoinError}
import coinffeine.protocol.messages.brokerage
import coinffeine.protocol.messages.brokerage.{OpenOrdersRequest, QuoteRequest}

/** Implementation of the topmost actor on a peer node. It starts all the relevant actors like
  * the peer actor and the message gateway and supervise them.
  */
class CoinffeinePeerActor(listenPort: Int,
                          brokerAddress: BrokerAddress,
                          props: CoinffeinePeerActor.PropsCatalogue) extends Actor with ActorLogging {
  import context.dispatcher
  import CoinffeinePeerActor._

  private val gatewayRef = spawnDelegate(props.gateway, "gateway")
  private val paymentProcessorRef = spawnDelegate(props.paymentProcessor, "paymentProcessor")
  private val bitcoinPeerRef = spawnDelegate(props.bitcoinPeer, "bitcoinPeer")
  private var walletRef: ActorRef = _
  private var orderSupervisorRef: ActorRef = _
  private var marketInfoRef: ActorRef = _

  override def receive: Receive = {

    case CoinffeinePeerActor.Connect =>
      val gatewayConnection = connectMessageGateway()
      val bitcoinConnection = connectBitcoinPeer()
      (for {
        brokerId <- gatewayConnection
        retrievedWalletRef <- bitcoinConnection
      } yield {
        walletRef = retrievedWalletRef
        orderSupervisorRef = spawnDelegate(props.orderSupervisor, "orders",
          OrderSupervisor.Initialize(
            brokerId, gatewayRef, paymentProcessorRef, bitcoinPeerRef, walletRef))
        marketInfoRef = spawnDelegate(
          props.marketInfo, "marketInfo", MarketInfoActor.Start(brokerId, gatewayRef))
        context.become(handleMessages)
        log.info("Coinffeine peer connected both to bitcoin and coinffeine networks")
        CoinffeinePeerActor.Connected
      }).recover {
        case NonFatal(cause) =>
          log.error(cause, "Coinffeine peer connection failed")
          CoinffeinePeerActor.ConnectionFailed(cause)
      }.pipeTo(sender())
  }

  private def connectMessageGateway(): Future[PeerId] = {
    implicit val timeout = CoinffeinePeerActor.ConnectionTimeout
    (gatewayRef ? MessageGateway.Join(listenPort, brokerAddress)).map {
      case MessageGateway.Joined(_, brokerId) =>
        brokerId
      case JoinError(cause) =>
        throw cause
    }
  }

  private def connectBitcoinPeer(): Future[ActorRef] = {
    implicit val timeout = CoinffeinePeerActor.ConnectionTimeout
    (bitcoinPeerRef ? BitcoinPeerActor.Start).map {
      case BitcoinPeerActor.Started(walletActor) =>
        walletActor
      case BitcoinPeerActor.StartFailure(cause) =>
        throw cause
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
          .withImmediateReply[MessageGateway.ConnectionStatus]()
      } yield {
        ConnectionStatus(bitcoinStatus, CoinffeineConnectionStatus(coinffeineStatus.activePeers))
      }).pipeTo(sender())
  }

  private def spawnDelegate(delegateProps: Props, name: String, initMessages: Any*): ActorRef = {
    val ref = context.actorOf(delegateProps, name)
    initMessages.foreach(ref ! _)
    ref
  }
}

/** Topmost actor on a peer node. */
object CoinffeinePeerActor {

  /** Start peer connection to the network. The sender of this message will receive either
    * a [[Connected]] or [[ConnectionFailed]] message in response. */
  case object Connect
  case object Connected
  case class ConnectionFailed(cause: Throwable)

  /** Message sent to the peer to get a [[ConnectionStatus]] in response */
  case object RetrieveConnectionStatus
  case class ConnectionStatus(bitcoinStatus: BitcoinConnectionStatus,
                              coinffeineStatus: CoinffeineConnectionStatus)

  /** Open a new order.
    *
    * Note that, in case of having a previous order at the same price, this means an increment
    * of its amount.
    *
    * @param order Order to open
    */
  case class OpenOrder(order: Order[FiatCurrency])

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
  case class RetrievedOpenOrders(orders: Seq[Order[FiatCurrency]])

  /** Ask for the currently open orders. To be replied with an [[brokerage.OpenOrders]]. */
  type RetrieveMarketOrders = brokerage.OpenOrdersRequest

  /** Ask for the current wallet balance */
  case object RetrieveWalletBalance

  /** Response for [[RetrieveWalletBalance]] */
  case class WalletBalance(amount: BitcoinAmount)

  private val PortSetting = "coinffeine.peer.port"
  private val BrokerHostnameSetting = "coinffeine.broker.hostname"
  private val BrokerPortSetting = "coinffeine.broker.port"

  private val ConnectionTimeout = Timeout(30.seconds)

  case class PropsCatalogue(gateway: Props,
                            marketInfo: Props,
                            orderSupervisor: Props,
                            bitcoinPeer: Props,
                            paymentProcessor: Props)

  trait Component { this: MessageGateway.Component
    with BitcoinPeerActor.Component
    with ExchangeActor.Component
    with ConfigComponent
    with NetworkComponent
    with ProtocolConstants.Component =>

    lazy val peerProps: Props = {
      val ownPort = config.getInt(PortSetting)
      val brokerHostname = config.getString(BrokerHostnameSetting)
      val brokerPort= config.getInt(BrokerPortSetting)
      val props = PropsCatalogue(
        messageGatewayProps(config),
        MarketInfoActor.props,
        OrderSupervisor.props(exchangeActorProps, config, network, protocolConstants),
        bitcoinPeerProps,
        OkPayProcessorActor.props(config)
      )
      Props(new CoinffeinePeerActor(ownPort, BrokerAddress(brokerHostname, brokerPort), props))
    }
  }
}
