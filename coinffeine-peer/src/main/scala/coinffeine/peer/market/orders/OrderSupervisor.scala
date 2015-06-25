package coinffeine.peer.market.orders

import scala.concurrent.duration._

import akka.actor._
import akka.pattern._
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout

import coinffeine.common.akka.AskPattern
import coinffeine.common.akka.event.CoinffeineEventProducer
import coinffeine.common.akka.persistence.{PeriodicSnapshot, PersistentEvent}
import coinffeine.model.order.{ActiveOrder, OrderId}
import coinffeine.peer.CoinffeinePeerActor._
import coinffeine.peer.events.network.OrderChanged
import coinffeine.peer.market.orders.archive.OrderArchive

/** Manages orders */
private[this] class OrderSupervisor(override val persistenceId: String,
                                    delegates: OrderSupervisor.Delegates)
  extends PersistentActor with PeriodicSnapshot with ActorLogging with CoinffeineEventProducer {

  import OrderSupervisor.OrderCreated

  private val submission = context.actorOf(delegates.submissionProps, "submission")
  private val archive = context.actorOf(delegates.archiveProps, "archive")
  private var orders = Map.empty[OrderId, ActiveOrder]
  private var orderRefs = Map.empty[OrderId, ActorRef]

  override val receiveRecover: Receive = {
    case event: OrderCreated => onOrderCreated(event)
    case SnapshotOffer(metadata, snapshot: OrderSupervisor.Snapshot) =>
      setLastSnapshot(metadata.sequenceNr)
      orders = snapshot.orders
    case RecoveryCompleted => retrieveArchivedOrders()
  }

  override protected def createSnapshot: Option[PersistentEvent] =
    Some(OrderSupervisor.Snapshot(orders))

  override val receiveCommand: Receive = retrievingArchivedOrders

  private def retrieveArchivedOrders(): Unit = {
    import context.dispatcher
    implicit val timeout = Timeout(OrderSupervisor.QueryTimeout)
    AskPattern(archive, OrderArchive.Query(), "cannot retrieve archived orders")
      .withReply[Any]()
      .pipeTo(self)
  }

  private def retrievingArchivedOrders: Receive = {
    case OrderArchive.QueryResponse(archivedOrders) =>
      archivedOrders.foreach(order => publish(OrderChanged(order)))
      val archivedIds = archivedOrders.map(_.id).toSet
      orders = orders.filterKeys(!archivedIds.contains(_))
      orders.values.foreach(spawn)
      log.info("Start accepting new orders")
      context.become(supervisingOrders)
      unstashAll()

    case OrderArchive.QueryError() =>
      throw new RuntimeException("Cannot retrieve archived orders")

    case Status.Failure(ex) =>
      throw new RuntimeException("Cannot retrieve archived orders", ex)

    case OpenOrder(_) | CancelOrder(_) => stash()
  }

  private def supervisingOrders: Receive = managingSnapshots orElse {
    case OpenOrder(request) =>
      persist(OrderCreated(request.create())){ event =>
        onOrderCreated(event)
        spawn(event.order)
        sender() ! OrderOpened(event.order)
      }

    case CancelOrder(orderId) =>
      orderRefs.get(orderId).foreach(_ ! OrderActor.CancelOrder)
      orderRefs = orderRefs.filterNot(_._1 == orderId)
  }

  private def onOrderCreated(event: OrderCreated): Unit = {
    orders += event.order.id -> event.order
  }

  private def spawn(order: ActiveOrder): Unit = {
    val props = delegates.orderActorProps(order, submission, archive)
    val ref = context.actorOf(props, s"order-${order.id.value}")
    orderRefs += order.id -> ref
  }
}

object OrderSupervisor {
  val DefaultPersistenceId = "orders"
  val QueryTimeout = 20.seconds

  private case class Snapshot(orders: Map[OrderId, ActiveOrder]) extends PersistentEvent

  trait Delegates {
    val submissionProps: Props
    def orderActorProps(order: ActiveOrder,
                        submission: ActorRef,
                        archive: ActorRef): Props
    val archiveProps: Props
  }

  def props(delegates: Delegates): Props = props(DefaultPersistenceId, delegates)

  def props(persistenceId: String, delegates: Delegates): Props =
    Props(new OrderSupervisor(persistenceId, delegates))

  private case class OrderCreated(order: ActiveOrder) extends PersistentEvent
}
