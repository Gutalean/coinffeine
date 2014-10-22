package coinffeine.peer.market.orders

import akka.actor._
import akka.persistence.PersistentActor

import coinffeine.common.akka.persistence.PersistentEvent
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{Order, OrderId}
import coinffeine.peer.CoinffeinePeerActor._

/** Manages orders */
private[this] class OrderSupervisor(override val persistenceId: String,
                                    delegates: OrderSupervisor.Delegates)
  extends PersistentActor with ActorLogging {

  import OrderSupervisor.OrderCreated

  private val submission = context.actorOf(delegates.submissionProps, "submission")
  private var orders = Map.empty[OrderId, ActorRef]

  override val receiveRecover: Receive = {
    case event: OrderCreated => onOrderCreated(event)
  }

  override val receiveCommand: Receive = {

    case OpenOrder(order) =>
      persist(OrderCreated(order))(onOrderCreated)

    case CancelOrder(orderId, reason) =>
      orders.get(orderId).foreach(_ ! OrderActor.CancelOrder(reason))
      orders = orders.filterNot(_._1 == orderId)
  }

  private def onOrderCreated(event: OrderCreated): Unit = {
    val ref = context.actorOf(
      delegates.orderActorProps(event.order, submission), s"order-${event.order.id.value}")
    orders += event.order.id -> ref
  }
}

object OrderSupervisor {
  val DefaultPersistenceId = "orders"

  trait Delegates {
    val submissionProps: Props
    def orderActorProps(order: Order[_ <: FiatCurrency], submission: ActorRef): Props
  }

  def props(delegates: Delegates): Props = props(DefaultPersistenceId, delegates)

  def props(persistenceId: String, delegates: Delegates): Props =
    Props(new OrderSupervisor(persistenceId, delegates))

  private case class OrderCreated(order: Order[_ <: FiatCurrency]) extends PersistentEvent
}
