package coinffeine.peer.market.orders

import akka.actor._

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{Order, OrderId}
import coinffeine.peer.CoinffeinePeerActor._

/** Manages orders */
private[this] class OrderSupervisor(delegates: OrderSupervisor.Delegates)
  extends Actor with ActorLogging {

  private val submission = context.actorOf(delegates.submissionProps, "submission")
  private var orders = Map.empty[OrderId, ActorRef]

  override def receive: Receive = {

    case OpenOrder(order) =>
      val ref = context.actorOf(
        delegates.orderActorProps(order, submission), s"order-${order.id.value}")
      orders += order.id -> ref

    case CancelOrder(orderId, reason) =>
      orders.get(orderId).foreach(_ ! OrderActor.CancelOrder(reason))
      orders = orders.filterNot(_._1 == orderId)
  }
}

object OrderSupervisor {
  trait Delegates {
    val submissionProps: Props
    def orderActorProps(order: Order[_ <: FiatCurrency], submission: ActorRef): Props
  }

  def props(delegates: Delegates): Props = Props(new OrderSupervisor(delegates))
}
