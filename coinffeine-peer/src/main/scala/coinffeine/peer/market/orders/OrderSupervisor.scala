package coinffeine.peer.market.orders

import akka.actor._
import akka.persistence.PersistentActor

import coinffeine.common.akka.persistence.PersistentEvent
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.order.{ActiveOrder, OrderId}
import coinffeine.peer.CoinffeinePeerActor._

/** Manages orders */
private[this] class OrderSupervisor(override val persistenceId: String,
                                    delegates: OrderSupervisor.Delegates)
  extends PersistentActor with ActorLogging {

  import OrderSupervisor.OrderCreated

  private val submission = context.actorOf(delegates.submissionProps, "submission")
  private val archive = context.actorOf(delegates.archiveProps, "archive")
  private var orders = Map.empty[OrderId, ActorRef]

  override val receiveRecover: Receive = {
    case event: OrderCreated => onOrderCreated(event)
  }

  override val receiveCommand: Receive = {

    case OpenOrder(request) =>
      persist(OrderCreated(request.create())){ event =>
        onOrderCreated(event)
        sender() ! OrderOpened(event.order)
      }

    case CancelOrder(orderId) =>
      orders.get(orderId).foreach(_ ! OrderActor.CancelOrder)
      orders = orders.filterNot(_._1 == orderId)
  }

  private def onOrderCreated(event: OrderCreated): Unit = {
    val props = delegates.orderActorProps(event.order, submission, archive)
    val ref = context.actorOf(props, s"order-${event.order.id.value}")
    orders += event.order.id -> ref
  }
}

object OrderSupervisor {
  val DefaultPersistenceId = "orders"

  trait Delegates {
    val submissionProps: Props
    def orderActorProps(order: ActiveOrder[_ <: FiatCurrency],
                        submission: ActorRef,
                        archive: ActorRef): Props
    val archiveProps: Props
  }

  def props(delegates: Delegates): Props = props(DefaultPersistenceId, delegates)

  def props(persistenceId: String, delegates: Delegates): Props =
    Props(new OrderSupervisor(persistenceId, delegates))

  private case class OrderCreated(order: ActiveOrder[_ <: FiatCurrency]) extends PersistentEvent
}
