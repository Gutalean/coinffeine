package coinffeine.peer

import scala.concurrent.duration._
import scala.util.Random

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout

import coinffeine.model.currency.FiatAmount
import coinffeine.model.market.{OrderBookEntry, OrderId}
import coinffeine.model.network.PeerId
import coinffeine.peer.event.EventChannelActor
import coinffeine.peer.market.{MarketInfoActor, OrderSupervisor}
import coinffeine.protocol.gateway.MessageGateway.{Bind, BindingError, BoundTo}
import coinffeine.protocol.gateway.{MessageGateway, PeerConnection}
import coinffeine.protocol.messages.brokerage
import coinffeine.protocol.messages.brokerage.{OpenOrdersRequest, QuoteRequest}
import com.coinffeine.common.config.ConfigComponent

/** Implementation of the topmost actor on a peer node. It starts all the relevant actors like
  * the peer actor and the message gateway and supervise them.
  */
class CoinffeinePeerActor(ownId: PeerId,
                          listenAddress: PeerConnection,
                          brokerId: PeerId,
                          brokerAddress: PeerConnection,
                          eventChannelProps: Props,
                          gatewayProps: Props,
                          marketInfoProps: Props,
                          orderSupervisorProps: Props) extends Actor with ActorLogging {
  import coinffeine.peer.CoinffeinePeerActor._
  import context.dispatcher

  val eventChannel: ActorRef = context.actorOf(eventChannelProps, "eventChannel")

  val gatewayRef = context.actorOf(gatewayProps, "gateway")
  val orderSupervisorRef = {
    val ref = context.actorOf(orderSupervisorProps, "orders")
    ref ! OrderSupervisor.Initialize(brokerId, eventChannel, gatewayRef)
    ref
  }
  val marketInfoRef = {
    val ref = context.actorOf(marketInfoProps)
    ref ! MarketInfoActor.Start(brokerId, gatewayRef)
    ref
  }

  override def receive: Receive = {

    case command @ CoinffeinePeerActor.Subscribe =>
      eventChannel.forward(command)
    case command @ CoinffeinePeerActor.Unsubscribe =>
      eventChannel.forward(command)

    case CoinffeinePeerActor.Connect =>
      implicit val timeout = CoinffeinePeerActor.ConnectionTimeout
      (gatewayRef ? Bind(ownId, listenAddress, brokerId, brokerAddress)).map {
        case BoundTo(_) => CoinffeinePeerActor.Connected
        case BindingError(cause) => CoinffeinePeerActor.ConnectionFailed(cause)
      }.pipeTo(sender())

    case BindingError(cause) =>
      log.error(cause, "Cannot start peer")
      context.stop(self)

    case QuoteRequest(market) =>
      marketInfoRef.tell(MarketInfoActor.RequestQuote(market), sender())
    case OpenOrdersRequest(market) =>
      marketInfoRef.tell(MarketInfoActor.RequestOpenOrders(market), sender())

    case openOrder: OpenOrder => orderSupervisorRef forward openOrder
    case cancelOrder: CancelOrder => orderSupervisorRef forward cancelOrder
    case message @ RetrieveOpenOrders => orderSupervisorRef forward message
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
  case class OpenOrder(order: OrderBookEntry[FiatAmount])

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
  case class RetrievedOpenOrders(orders: Seq[OrderBookEntry[FiatAmount]])

  /** Ask for the currently open orders. To be replied with an [[brokerage.OpenOrders]]. */
  type RetrieveMarketOrders = brokerage.OpenOrdersRequest

  private val HostSetting = "coinffeine.peer.host"
  private val PortSetting = "coinffeine.peer.port"
  private val BrokerIdSetting = "coinffeine.broker.id"
  private val BrokerAddressSetting = "coinffeine.broker.address"

  private val ConnectionTimeout = Timeout(10.seconds)

  trait Component { this: OrderSupervisor.Component with MarketInfoActor.Component
    with MessageGateway.Component with ConfigComponent =>

    lazy val peerProps: Props = {
      val ownId = PeerId("client" + Random.nextInt(1000))
      val ownAddress = PeerConnection(config.getString(HostSetting), config.getInt(PortSetting))
      val brokerId = PeerId(config.getString(BrokerIdSetting))
      val brokerAddress = PeerConnection.parse(config.getString(BrokerAddressSetting))
      Props(new CoinffeinePeerActor(
        ownId,
        ownAddress,
        brokerId,
        brokerAddress,
        eventChannelProps = EventChannelActor.props(),
        gatewayProps = messageGatewayProps,
        marketInfoProps,
        orderSupervisorProps
      ))
    }
  }
}