package coinffeine.peer.market.orders

import scala.annotation.tailrec
import scala.util.{Failure, Success}

import akka.actor._
import akka.event.Logging
import akka.persistence._
import org.bitcoinj.core.NetworkParameters
import org.joda.time.DateTime

import coinffeine.common.akka.event.CoinffeineEventProducer
import coinffeine.common.akka.persistence.{PeriodicSnapshot, PersistentEvent}
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.{BrokerId, PeerId}
import coinffeine.model.order.ActiveOrder
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.peer.events.network.OrderChanged
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.market.orders.archive.OrderArchive.{ArchiveOrder, CannotArchive, OrderArchived}
import coinffeine.peer.market.orders.controller._
import coinffeine.peer.market.orders.funds.FundsBlockerActor
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, ReceiveMessage}
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.messages.handshake.ExchangeRejection

class OrderActor(
    initialOrder: ActiveOrder,
    order: OrderController,
    delegates: OrderActor.Delegates,
    collaborators: OrderActor.Collaborators)
    extends PersistentActor with PeriodicSnapshot with ActorLogging
    with OrderPublisher.Listener with CoinffeineEventProducer {

  import OrderActor._

  private val orderId = initialOrder.id
  override val persistenceId: String = s"order-${orderId.value}"
  private val currency = initialOrder.price.currency
  private val publisher = new OrderPublisher(orderId, collaborators.submissionSupervisor, this)

  override def preStart(): Unit = {
    log.info("Order actor initialized for {}", orderId)
    subscribeToOrderMatches()
    subscribeToOrderChanges()
    super.preStart()
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info("Stopped order actor for {}", orderId)
  }

  override def receiveRecover: Receive = {
    case SnapshotOffer(metadata, snapshot: Snapshot) =>
      setLastSnapshot(metadata.sequenceNr)
      order.reset(snapshot.order, snapshot.pendingFunds)
    case event: FundsRequested => onFundsRequested(event.asInstanceOf[FundsRequested])
    case event: FundsBlocked => onFundsBlocked(event)
    case event: CannotBlockFunds => onCannotBlockFunds(event)
    case event: CancelledOrder => onCancelledOrder(event)
    case event: ExchangeFinished => onCompletedExchange(event.asInstanceOf[ExchangeFinished])
    case RecoveryCompleted => self ! ResumeOrder
  }

  private def onFundsRequested(event: FundsRequested): Unit = {
    order.fundsRequested(event.orderMatch, event.requiredFunds)
  }

  private def onFundsBlocked(event: FundsBlocked): Unit = {
    val exchange = order.startExchange(event.exchangeId, event.timestamp)
    if (!recoveryRunning) {
      spawnExchangeActor(exchange)
    }
  }

  private def onCannotBlockFunds(event: CannotBlockFunds): Unit = {
    order.fundsRequestFailed(event.exchangeId)
  }

  private def onCancelledOrder(event: CancelledOrder): Unit = {
    order.cancel(event.timestamp)
  }

  private def onCompletedExchange(event: ExchangeFinished): Unit = {
    order.completeExchange(event.exchange)
  }

  override protected def createSnapshot: Option[PersistentEvent] =
    Some(Snapshot(order.view, order.pendingFundRequests))

  override def receiveCommand = managingSnapshots orElse publisher.receiveSubmissionEvents orElse {
    case ResumeOrder => resumeOrder()

    case ReceiveMessage(message: OrderMatch, _) if order.hasPendingFunds(message.exchangeId) =>
      log.warning("Already blocking funds for {}, should take so long", message.exchangeId)

    case ReceiveMessage(message: OrderMatch, _) if message.currency == currency =>
      val orderMatch = message.asInstanceOf[OrderMatch]
      order.shouldAcceptOrderMatch(orderMatch) match {
        case MatchAccepted(requiredFunds) =>
          persist(FundsRequested(orderMatch, requiredFunds)) { event =>
            log.info("Blocking funds for {}", orderMatch)
            spawnFundsBlocker(orderMatch.exchangeId, requiredFunds)
            onFundsRequested(event)
          }
        case MatchRejected(cause) =>
          rejectOrderMatch(ExchangeRejection.InvalidOrderMatch, orderMatch.exchangeId)
        case MatchAlreadyAccepted(oldExchange) =>
          log.debug("Received order match for the already accepted exchange {}", oldExchange.id)
      }

    case FundsBlockerActor.BlockingResult(exchangeId, result) if !order.hasPendingFunds(exchangeId) =>
      log.warning("Unexpected blocking result {} for {}", result, exchangeId)

    case FundsBlockerActor.BlockingResult(exchangeId, Success(_)) =>
      persist(FundsBlocked(exchangeId, DateTime.now())) { event =>
        log.info("Accepting {}, funds just got blocked", exchangeId)
        onFundsBlocked(event)
      }

    case FundsBlockerActor.BlockingResult(exchangeId, Failure(cause)) =>
      persist(CannotBlockFunds(exchangeId)) { event =>
        log.error(cause, "Cannot block funds for {}", exchangeId)
        rejectOrderMatch(ExchangeRejection.UnavailableFunds, exchangeId)
        onCannotBlockFunds(event)
      }

    case CancelOrder =>
      log.info("Cancelling order {}", orderId)
      persist(CancelledOrder(DateTime.now()))(onCancelledOrder)

    case ExchangeActor.ExchangeUpdate(exchange) if exchange.currency == currency =>
      log.debug("Order actor received update for {}: {}", exchange.id, exchange.progress)
      order.updateExchange(exchange.asInstanceOf[ActiveExchange])

    case ExchangeActor.ExchangeSuccess(exchange) if exchange.currency == currency =>
      completeExchange(exchange.asInstanceOf[SuccessfulExchange])

    case ExchangeActor.ExchangeFailure(exchange) if exchange.currency == currency =>
      completeExchange(exchange.asInstanceOf[FailedExchange])

    case OrderArchived(`orderId`) =>
      log.info("{}: archived and stopping", orderId)
      deleteMessages(lastSequenceNr)
      deleteSnapshots(SnapshotSelectionCriteria.Latest)
      self ! PoisonPill

    case CannotArchive(`orderId`) =>
      log.error("{}: Cannot archive myself. Keeping awake.", orderId)
  }

  private def resumeOrder(): Unit = {
    reRequestPendingFunds()
    val currentOrder = order.view
    publish(OrderChanged(currentOrder))
    updatePublisher(currentOrder)
    for (exchange <- currentOrder.exchanges.values if !exchange.isCompleted) {
      spawnExchangeActor(exchange)
    }
  }

  private def spawnExchangeActor(exchange: ActiveExchange): ActorRef = {
    context.actorOf(delegates.exchangeActor(handshakingExchangeOf(exchange)), exchange.id.value)
  }

  @tailrec
  private def handshakingExchangeOf(exchange: ActiveExchange): HandshakingExchange =
    exchange match {
      case ex: HandshakingExchange => ex
      case ex: DepositPendingExchange => ex.prev
      case ex: RunningExchange => ex.prev.prev
      case ex: SuccessfulExchange => ex.prev.prev.prev
      case ex: AbortingExchange => handshakingExchangeOf(ex.prev)
      case ex: FailedExchange => handshakingExchangeOf(ex.prev)
    }

  override def inMarket(): Unit = {
    order.becomeInMarket()
  }

  override def offline(): Unit = {
    order.becomeOffline()
  }

  private def reRequestPendingFunds(): Unit = {
    order.pendingFunds.foreach { case (exchangeId, requiredFunds) =>
      spawnFundsBlocker(exchangeId, requiredFunds)
    }
  }

  private def subscribeToOrderMatches(): Unit = {
    collaborators.gateway ! MessageGateway.Subscribe.fromBroker {
      case orderMatch: OrderMatch if orderMatch.orderId == orderId &&
          orderMatch.currency == currency =>
    }
  }

  private def subscribeToOrderChanges(): Unit = {
    order.addListener(new OrderController.Listener {
      override def onOrderChange(oldOrder: ActiveOrder, newOrder: ActiveOrder): Unit = {
        if (recoveryFinished) {
          if (newOrder.status != oldOrder.status) {
            log.info("Order {} has now {} status", orderId, newOrder.status)
          }
          if (newOrder.progress != oldOrder.progress) {
            log.debug("Order {} progress: {}%", orderId, (100 * newOrder.progress).formatted("%5.2f"))
          }
          publish(OrderChanged(newOrder))
          updatePublisher(newOrder)
          if (!newOrder.status.isActive) {
            requestArchivation()
          }
        }
      }
    })
  }

  private def updatePublisher(order: ActiveOrder): Unit = {
    if (order.shouldBeOnMarket) publisher.setEntry(order.pendingOrderBookEntry)
    else publisher.unsetEntry()
  }

  private def rejectOrderMatch(
      cause: ExchangeRejection.Cause, exchangeId: ExchangeId): Unit = {
    log.info("Rejecting match for {}: {}", exchangeId, cause.message)
    val rejection = ExchangeRejection(exchangeId, cause)
    collaborators.gateway ! ForwardMessage(rejection, BrokerId)
  }

  private def completeExchange(exchange: CompletedExchange): Unit = {
    val level = if (exchange.isSuccess) Logging.InfoLevel else Logging.ErrorLevel
    log.log(level, "Exchange {}: completed with state {}", exchange.id, exchange.status)
    persist(ExchangeFinished(exchange)) { event =>
      onCompletedExchange(event)
      sender() ! ExchangeActor.FinishExchange
    }
  }

  private def spawnFundsBlocker(exchangeId: ExchangeId, funds: RequiredFunds): Unit = {
    context.actorOf(delegates.fundsBlocker(exchangeId, funds))
  }

  private def requestArchivation(): Unit = {
    collaborators.archive ! ArchiveOrder(order.view)
  }
}

object OrderActor {

  private case class Snapshot(
      order: ActiveOrder,
      pendingFunds: Map[ExchangeId, OrderController.FundsRequest]) extends PersistentEvent

  case class Collaborators(
      wallet: ActorRef,
      paymentProcessor: ActorRef,
      submissionSupervisor: ActorRef,
      gateway: ActorRef,
      bitcoinPeer: ActorRef,
      blockchain: ActorRef,
      archive: ActorRef)

  trait Delegates {
    def exchangeActor(exchange: HandshakingExchange)(implicit context: ActorContext): Props

    def fundsBlocker(id: ExchangeId, funds: RequiredFunds)
        (implicit context: ActorContext): Props
  }

  case object CancelOrder

  def props(
      exchangeActorProps: (HandshakingExchange, ExchangeActor.Collaborators) => Props,
      network: NetworkParameters,
      amountsCalculator: AmountsCalculator,
      order: ActiveOrder,
      collaborators: Collaborators,
      peerId: PeerId): Props = {
    import collaborators._
    val delegates = new Delegates {
      override def exchangeActor(exchange: HandshakingExchange)
          (implicit context: ActorContext) = {
        exchangeActorProps(exchange, ExchangeActor.Collaborators(
          wallet, paymentProcessor, gateway, bitcoinPeer, blockchain, context.self))
      }

      override def fundsBlocker(id: ExchangeId, funds: RequiredFunds)
          (implicit context: ActorContext) =
        FundsBlockerActor.props(id, wallet, paymentProcessor, funds, context.self)
    }
    Props(new OrderActor(
      order,
      new OrderController(peerId, amountsCalculator, network, order),
      delegates,
      collaborators
    ))
  }

  private case object ResumeOrder

  private case class FundsRequested(
      orderMatch: OrderMatch, requiredFunds: RequiredFunds) extends PersistentEvent

  private case class FundsBlocked(exchangeId: ExchangeId, timestamp: DateTime)
      extends PersistentEvent

  private case class CannotBlockFunds(exchangeId: ExchangeId) extends PersistentEvent

  private case class CancelledOrder(timestamp: DateTime) extends PersistentEvent

  private case class ExchangeFinished(exchange: CompletedExchange)
      extends PersistentEvent

}
