package coinffeine.peer

import scala.concurrent.duration._

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout

import coinffeine.model.currency.{BitcoinAmount, FiatCurrency}
import coinffeine.model.market.{Order, OrderId}
import coinffeine.peer.bitcoin.{BitcoinPeerActor, WalletActor}
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.event.EventChannelActor
import coinffeine.peer.market.{MarketInfoActor, OrderSupervisor}
import coinffeine.peer.payment.PaymentProcessor
import coinffeine.peer.payment.PaymentProcessor.RetrieveBalance
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.{ConnectingError, BindingError, BrokerAddress}
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

  private val eventChannel = spawnDelegate(props.eventChannel, "eventChannel")
  private val gatewayRef = spawnDelegate(props.gateway, "gateway")
  private val paymentProcessorRef = spawnDelegate(
    props.paymentProcessor, "paymentProcessor", PaymentProcessor.Initialize(eventChannel))
  private val bitcoinPeerRef = spawnDelegate(props.bitcoinPeer, "bitcoinPeer")
  private val walletRef = spawnDelegate(props.wallet, "wallet", WalletActor.Initialize(eventChannel))
  private var orderSupervisorRef: ActorRef = _
  private var marketInfoRef: ActorRef = _

  override def receive: Receive = {

    case CoinffeinePeerActor.Connect =>
      implicit val timeout = CoinffeinePeerActor.ConnectionTimeout
      (gatewayRef ? MessageGateway.Connect(listenPort, brokerAddress)).map {
        case MessageGateway.Connected(_, brokerId) =>
          orderSupervisorRef = spawnDelegate(props.orderSupervisor, "orders",
            OrderSupervisor.Initialize(
              brokerId, eventChannel, gatewayRef, paymentProcessorRef, bitcoinPeerRef, walletRef))
          marketInfoRef = spawnDelegate(
            props.marketInfo, "marketInfo", MarketInfoActor.Start(brokerId, gatewayRef))
          context.become(handleMessages)
          CoinffeinePeerActor.Connected
        case ConnectingError(cause) => CoinffeinePeerActor.ConnectionFailed(cause)
      }.pipeTo(sender())

    case BindingError(cause) =>
      log.error(cause, "Cannot start peer")
      context.stop(self)
  }

  private val handleMessages: Receive = {
    case message @ (CoinffeinePeerActor.Subscribe | CoinffeinePeerActor.Unsubscribe) =>
      eventChannel forward message
    case message @ (OpenOrder(_) | CancelOrder(_) | RetrieveOpenOrders) =>
      orderSupervisorRef forward message
    case message @ RetrieveWalletBalance =>
      walletRef forward message
    case message @ RetrieveBalance(_) =>
      paymentProcessorRef forward message

    case QuoteRequest(market) =>
      marketInfoRef.forward(MarketInfoActor.RequestQuote(market))
    case OpenOrdersRequest(market) =>
      marketInfoRef.forward(MarketInfoActor.RequestOpenOrders(market))
  }

  private def spawnDelegate(delegateProps: Props, name: String, initMessages: Any*): ActorRef = {
    val ref = context.actorOf(delegateProps, name)
    initMessages.foreach(ref ! _)
    ref
  }
}

/** Topmost actor on a peer node. */
object CoinffeinePeerActor {

  /** A message sent to request the subscription to events for the sender. */
  case object Subscribe

  /** A message sent to request the unsubscription to events for the sender. */
  case object Unsubscribe

  /** Start peer connection to the network. The sender of this message will receive either
    * a [[Connected]] or [[ConnectionFailed]] message in response. */
  case object Connect
  case object Connected
  case class ConnectionFailed(cause: Throwable)

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
    */
  case class CancelOrder(order: OrderId)

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

  private val ConnectionTimeout = Timeout(10.seconds)

  case class PropsCatalogue(eventChannel: Props,
                            gateway: Props,
                            marketInfo: Props,
                            orderSupervisor: Props,
                            bitcoinPeer: Props,
                            wallet: Props,
                            paymentProcessor: Props)

  trait Component { this: OrderSupervisor.Component
    with MarketInfoActor.Component
    with MessageGateway.Component
    with BitcoinPeerActor.Component
    with WalletActor.Component
    with PaymentProcessor.Component
    with ConfigComponent =>

    lazy val peerProps: Props = {
      val ownPort = config.getInt(PortSetting)
      val brokerHostname = config.getString(BrokerHostnameSetting)
      val brokerPort= config.getInt(BrokerPortSetting)
      val props = PropsCatalogue(
        EventChannelActor.props(),
        messageGatewayProps,
        marketInfoProps,
        orderSupervisorProps,
        bitcoinPeerProps,
        walletActorProps,
        paymentProcessorProps
      )
      Props(new CoinffeinePeerActor(ownPort, BrokerAddress(brokerHostname, brokerPort), props))
    }
  }
}
