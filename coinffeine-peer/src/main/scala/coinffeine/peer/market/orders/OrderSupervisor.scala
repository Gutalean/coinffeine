package coinffeine.peer.market.orders

import akka.actor._
import akka.pattern._
import akka.persistence.{SnapshotOffer, PersistentActor, RecoveryCompleted}

import coinffeine.common.akka.AskPattern
import coinffeine.common.akka.persistence.{PeriodicSnapshot, PersistentEvent}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.network.MutableCoinffeineNetworkProperties
import coinffeine.model.order.{ActiveOrder, AnyCurrencyActiveOrder, OrderId}
import coinffeine.peer.CoinffeinePeerActor._
import coinffeine.peer.market.orders.archive.OrderArchive

/** Manages orders */
private[this] class OrderSupervisor(override val persistenceId: String,
                                    delegates: OrderSupervisor.Delegates,
                                    properties: MutableCoinffeineNetworkProperties)
  extends PersistentActor with PeriodicSnapshot with ActorLogging {

  import OrderSupervisor.OrderCreated

  private val submission = context.actorOf(delegates.submissionProps, "submission")
  private val archive = context.actorOf(delegates.archiveProps, "archive")
  private var orders = Map.empty[OrderId, AnyCurrencyActiveOrder]
  private var orderRefs = Map.empty[OrderId, ActorRef]

  override val receiveRecover: Receive = {
    case event: OrderCreated => onOrderCreated(event)
    case SnapshotOffer(_, snapshot: OrderSupervisor.Snapshot) => orders = snapshot.orders
    case RecoveryCompleted => retrieveArchivedOrders()
  }

  override val receiveCommand: Receive = retrievingArchivedOrders

  private def retrieveArchivedOrders(): Unit = {
    import context.dispatcher
    AskPattern(archive, OrderArchive.Query(), "cannot retrieve archived orders")
      .withImmediateReply[Any]()
      .pipeTo(self)
  }

  private def retrievingArchivedOrders: Receive = {
    case OrderArchive.QueryResponse(archivedOrders) =>
      archivedOrders.foreach(order => properties.orders.set(order.id, order))
      val archivedIds = archivedOrders.map(_.id).toSet
      orders = orders.filterKeys(!archivedIds.contains(_))
      orders.values.foreach(spawn)
      context.become(supervisingOrders)
      unstashAll()

    case OrderArchive.QueryError() =>
      throw new RuntimeException("Cannot retrieve archived orders")

    case OpenOrder(_) | CancelOrder(_) => stash()
  }

  private def supervisingOrders: Receive = deletingSnapshots orElse {
    case OpenOrder(request) =>
      persist(OrderCreated(request.create())){ event =>
        onOrderCreated(event)
        spawn(event.order)
        sender() ! OrderOpened(event.order)
      }

    case CancelOrder(orderId) =>
      orderRefs.get(orderId).foreach(_ ! OrderActor.CancelOrder)
      orderRefs = orderRefs.filterNot(_._1 == orderId)

    case PeriodicSnapshot.CreateSnapshot => saveSnapshot(OrderSupervisor.Snapshot(orders))
  }

  private def onOrderCreated(event: OrderCreated): Unit = {
    orders += event.order.id -> event.order
  }

  private def spawn(order: AnyCurrencyActiveOrder): Unit = {
    val props = delegates.orderActorProps(order, submission, archive)
    val ref = context.actorOf(props, s"order-${order.id.value}")
    orderRefs += order.id -> ref
  }
}

object OrderSupervisor {
  val DefaultPersistenceId = "orders"

  private case class Snapshot(orders: Map[OrderId, AnyCurrencyActiveOrder])

  trait Delegates {
    val submissionProps: Props
    def orderActorProps(order: ActiveOrder[_ <: FiatCurrency],
                        submission: ActorRef,
                        archive: ActorRef): Props
    val archiveProps: Props
  }

  def props(delegates: Delegates, properties: MutableCoinffeineNetworkProperties): Props =
    props(DefaultPersistenceId, delegates, properties)

  def props(persistenceId: String,
            delegates: Delegates,
            properties: MutableCoinffeineNetworkProperties): Props =
    Props(new OrderSupervisor(persistenceId, delegates, properties))

  private case class OrderCreated(order: ActiveOrder[_ <: FiatCurrency]) extends PersistentEvent
}
