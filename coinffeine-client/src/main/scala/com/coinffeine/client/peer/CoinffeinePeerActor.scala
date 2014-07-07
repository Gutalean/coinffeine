package com.coinffeine.client.peer

import scala.concurrent.duration._

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern._
import akka.util.Timeout
import com.googlecode.protobuf.pro.duplex.PeerInfo

import com.coinffeine.client.peer.CoinffeinePeerActor.{CancelOrder, OpenOrder}
import com.coinffeine.client.peer.orders.OrdersActor
import com.coinffeine.common.{Order, PeerConnection}
import com.coinffeine.common.config.ConfigComponent
import com.coinffeine.common.protocol.gateway.MessageGateway
import com.coinffeine.common.protocol.gateway.MessageGateway.{Bind, BindingError, BoundTo}
import com.coinffeine.common.protocol.messages.brokerage.QuoteRequest

/** Implementation of the topmost actor on a peer node. It starts all the relevant actors like
  * the peer actor and the message gateway and supervise them.
  */
class CoinffeinePeerActor(address: PeerInfo,
                          brokerAddress: PeerConnection,
                          gatewayProps: Props,
                          quoteRequestProps: Props,
                          ordersActorProps: Props) extends Actor with ActorLogging {

  import context.dispatcher

  val gatewayRef = context.actorOf(gatewayProps, "gateway")
  val ordersActorRef = {
    val ref = context.actorOf(ordersActorProps, "orders")
    ref ! OrdersActor.Initialize(gatewayRef, brokerAddress)
    ref
  }

  override def receive: Receive = {

    case CoinffeinePeerActor.Connect =>
      implicit val timeout = CoinffeinePeerActor.ConnectionTimeout
      (gatewayRef ? Bind(address)).map {
        case BoundTo(_) => CoinffeinePeerActor.Connected
        case BindingError(cause) => CoinffeinePeerActor.ConnectionFailed(cause)
      }.pipeTo(sender())

    case BindingError(cause) =>
      log.error(cause, "Cannot start peer")
      context.stop(self)

    case QuoteRequest(currency) =>
      val request = QuoteRequestActor.StartRequest(currency, gatewayRef, brokerAddress)
      context.actorOf(quoteRequestProps) forward request

    case openOrder: OpenOrder => ordersActorRef ! openOrder
    case cancelOrder: CancelOrder => ordersActorRef ! cancelOrder
  }
}

/** Topmost actor on a peer node. */
object CoinffeinePeerActor {

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
  case class OpenOrder(order: Order)

  /** Cancel an order
    *
    * Note that this can cancel partially an existing order for a greater amount of bitcoin.
    *
    * @param order  Order to cancel
    */
  case class CancelOrder(order: Order)

  private val HostSetting = "coinffeine.peer.host"
  private val PortSetting = "coinffeine.peer.port"
  private val BrokerAddressSetting = "coinffeine.broker.address"

  private val ConnectionTimeout = Timeout(10.seconds)

  trait Component {
    this: QuoteRequestActor.Component with OrdersActor.Component
      with MessageGateway.Component with ConfigComponent =>

    lazy val peerProps: Props = {
      val peerInfo = new PeerInfo(config.getString(HostSetting), config.getInt(PortSetting))
      val brokerAddress = PeerConnection.parse(config.getString(BrokerAddressSetting))
      Props(new CoinffeinePeerActor(
        peerInfo,
        brokerAddress,
        gatewayProps = messageGatewayProps,
        quoteRequestProps = quoteRequestProps,
        ordersActorProps = ordersActorProps
      ))
    }
  }
}
