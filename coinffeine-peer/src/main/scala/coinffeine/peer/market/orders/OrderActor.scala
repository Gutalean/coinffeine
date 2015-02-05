package coinffeine.peer.market.orders

import scala.util.{Failure, Success}

import akka.actor._
import akka.event.Logging
import akka.persistence.{RecoveryCompleted, PersistentActor}
import org.bitcoinj.core.NetworkParameters

import coinffeine.common.akka.persistence.PersistentEvent
import coinffeine.model.currency._
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.{PeerId, BrokerId, MutableCoinffeineNetworkProperties}
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.market.orders.controller._
import coinffeine.peer.market.orders.funds.FundsBlockerActor
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, ReceiveMessage}
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.messages.handshake.ExchangeRejection

class OrderActor[C <: FiatCurrency](
    initialOrder: Order[C],
    order: OrderController[C],
    delegates: OrderActor.Delegates[C],
    coinffeineProperties: MutableCoinffeineNetworkProperties,
    collaborators: OrderActor.Collaborators)
  extends PersistentActor with ActorLogging with OrderPublisher.Listener {

  import OrderActor._

  private case class FundRequest(orderMatch: OrderMatch[C], funds: RequiredFunds[C]) {
    def start(): Unit = {
      context.actorOf(delegates.fundsBlocker(orderMatch.exchangeId, funds))
    }
  }

  private val orderId = initialOrder.id
  override val persistenceId: String = s"order-${orderId.value}"
  private val currency = initialOrder.price.currency
  private val publisher = new OrderPublisher[C](collaborators.submissionSupervisor, this)
  private var pendingFundRequests = Map.empty[ExchangeId, FundRequest]

  override def preStart(): Unit = {
    log.info("Order actor initialized for {}", orderId)
    subscribeToOrderMatches()
    subscribeToOrderChanges()
    super.preStart()
  }

  override def receiveRecover: Receive = {
    case OrderStarted => onOrderStarted()
    case event: FundsRequested[_] => onFundsRequested(event.asInstanceOf[FundsRequested[C]])
    case event: FundsBlocked => onFundsBlocked(event)
    case event: CannotBlockFunds => onCannotBlockFunds(event)
    case CancelledOrder => onCancelledOrder()
    case RecoveryCompleted => self ! ResumeOrder
  }

  private def onOrderStarted(): Unit = {
    order.start()
  }

  private def onFundsRequested(event: FundsRequested[C]): Unit = {
    pendingFundRequests +=
      event.orderMatch.exchangeId -> FundRequest(event.orderMatch, event.requiredFunds)
  }

  private def onFundsBlocked(event: FundsBlocked): Unit = {
    val blockingInProgress = pendingFundRequests(event.exchangeId)
    val exchange = order.acceptOrderMatch(blockingInProgress.orderMatch)
    pendingFundRequests -= event.exchangeId
    context.actorOf(delegates.exchangeActor(exchange), exchange.id.value)
  }

  private def onCannotBlockFunds(event: CannotBlockFunds): Unit = {
    pendingFundRequests -= event.exchangeId
  }

  private def onCancelledOrder(): Unit = {
    order.cancel()
  }

  override def receiveCommand = publisher.receiveSubmissionEvents orElse {
    case ResumeOrder => resumeOrder()

    case ReceiveMessage(message: OrderMatch[_], _) if message.currency == currency =>
      val orderMatch = message.asInstanceOf[OrderMatch[C]]
      order.shouldAcceptOrderMatch(orderMatch) match {
        case MatchAccepted(requiredFunds) =>
          persist(FundsRequested(orderMatch, requiredFunds)) { event =>
            log.info("Blocking funds for {}", orderMatch)
            onFundsRequested(event)
            pendingFundRequests(event.orderMatch.exchangeId).start()
          }
        case MatchRejected(cause) =>
          rejectOrderMatch(cause, orderMatch)
        case MatchAlreadyAccepted(oldExchange) =>
          log.debug("Received order match for the already accepted exchange {}", oldExchange.id)
      }

    case FundsBlockerActor.BlockingResult(exchangeId, result)
      if !pendingFundRequests.contains(exchangeId) =>
      log.warning("Unexpected blocking result {} for {}", result, exchangeId)

    case FundsBlockerActor.BlockingResult(exchangeId, Success(_)) =>
      val orderMatch = pendingFundRequests(exchangeId).orderMatch
      persist(FundsBlocked(exchangeId)) { event =>
        log.info("Accepting match for {} against counterpart {} identified as {}",
          orderId, orderMatch.counterpart, orderMatch.exchangeId)
        onFundsBlocked(event)
      }

    case FundsBlockerActor.BlockingResult(exchangeId, Failure(cause)) =>
      persist(CannotBlockFunds(exchangeId)) { event =>
        log.error(cause, "Cannot block funds")
        rejectOrderMatch("Cannot block funds", pendingFundRequests(exchangeId).orderMatch)
        onCannotBlockFunds(event)
      }

    case CancelOrder =>
      log.info("Cancelling order {}", orderId)
      persist(CancelledOrder)(_ => onCancelledOrder())

    case ExchangeActor.ExchangeUpdate(exchange) if exchange.currency == currency =>
      log.debug("Order actor received update for {}: {}", exchange.id, exchange.progress)
      order.updateExchange(exchange.asInstanceOf[Exchange[C]])

    case ExchangeActor.ExchangeSuccess(exchange) if exchange.currency == currency =>
      completeExchange(exchange.asInstanceOf[SuccessfulExchange[C]])

    case ExchangeActor.ExchangeFailure(exchange) if exchange.currency == currency =>
      completeExchange(exchange.asInstanceOf[FailedExchange[C]])
  }

  private def resumeOrder(): Unit = {
    reRequestPendingFunds()
    val currentOrder = order.view
    coinffeineProperties.orders.set(currentOrder.id, currentOrder)
    updatePublisher(currentOrder)
    if (!currentOrder.started) {
      persist(OrderStarted) { _ =>
        coinffeineProperties.orders.set(orderId, initialOrder)
        onOrderStarted()
      }
    }
  }

  override def inMarket(): Unit = { order.becomeInMarket() }
  override def offline(): Unit = { order.becomeOffline() }

  private def reRequestPendingFunds(): Unit = {
    pendingFundRequests.values.foreach(_.start())
  }

  private def subscribeToOrderMatches(): Unit = {
    collaborators.gateway ! MessageGateway.Subscribe.fromBroker {
      case orderMatch: OrderMatch[_] if orderMatch.orderId == orderId &&
        orderMatch.currency == currency =>
    }
  }

  private def subscribeToOrderChanges(): Unit = {
    order.addListener(new OrderController.Listener[C] {
      override def onOrderChange(oldOrder: Order[C], newOrder: Order[C]): Unit = {
        if (recoveryFinished) {
          if (newOrder.status != oldOrder.status) {
            log.info("Order {} has now {} status", orderId, newOrder.status)
          }
          if (newOrder.progress != oldOrder.progress) {
            log.debug("Order {} progress: {}%", orderId, (100 * newOrder.progress).formatted("%5.2f"))
          }
          coinffeineProperties.orders.set(newOrder.id, newOrder)
          updatePublisher(newOrder)
        }
      }
    })
  }

  private def updatePublisher(order: Order[C]): Unit = {
    if (order.shouldBeOnMarket) publisher.keepPublishing(order.pendingOrderBookEntry)
    else publisher.stopPublishing()
  }

  private def rejectOrderMatch(cause: String, rejectedMatch: OrderMatch[_]): Unit = {
    log.info("Rejecting match for {} against counterpart {}: {}",
      orderId, rejectedMatch.counterpart, cause)
    val rejection = ExchangeRejection(rejectedMatch.exchangeId, cause)
    collaborators.gateway ! ForwardMessage(rejection, BrokerId)
  }

  private def completeExchange(exchange: CompletedExchange[C]): Unit = {
    val level = if (exchange.isSuccess) Logging.InfoLevel else Logging.ErrorLevel
    log.log(level, "Exchange {}: completed with state {}", exchange.id, exchange.status)
    order.completeExchange(exchange)
  }
}

object OrderActor {
  case class Collaborators(wallet: ActorRef,
                           paymentProcessor: ActorRef,
                           submissionSupervisor: ActorRef,
                           gateway: ActorRef,
                           bitcoinPeer: ActorRef,
                           blockchain: ActorRef)

  trait Delegates[C <: FiatCurrency] {
    def exchangeActor(exchange: HandshakingExchange[C])(implicit context: ActorContext): Props
    def fundsBlocker(id: ExchangeId, funds: RequiredFunds[C])(implicit context: ActorContext): Props
  }

  case object CancelOrder

  def props[C <: FiatCurrency](exchangeActorProps: (HandshakingExchange[C], ExchangeActor.Collaborators) => Props,
                               network: NetworkParameters,
                               amountsCalculator: AmountsCalculator,
                               order: Order[C],
                               coinffeineProperties: MutableCoinffeineNetworkProperties,
                               collaborators: Collaborators,
                               peerId: PeerId): Props = {
    import collaborators._
    val delegates = new Delegates[C] {
      override def exchangeActor(exchange: HandshakingExchange[C])(implicit context: ActorContext) = {
        exchangeActorProps(exchange, ExchangeActor.Collaborators(
          wallet, paymentProcessor, gateway, bitcoinPeer, blockchain, context.self))
      }
      override def fundsBlocker(id: ExchangeId, funds: RequiredFunds[C])
                               (implicit context: ActorContext) =
        FundsBlockerActor.props(id, wallet, paymentProcessor, funds, context.self)
    }
    Props(new OrderActor[C](
      order,
      new OrderController(peerId, amountsCalculator, network, order),
      delegates,
      coinffeineProperties,
      collaborators
    ))
  }

  private case object ResumeOrder
  private case object OrderStarted extends PersistentEvent
  private case class FundsRequested[C <: FiatCurrency](
      orderMatch: OrderMatch[C], requiredFunds: RequiredFunds[C]) extends PersistentEvent
  private case class FundsBlocked(exchangeId: ExchangeId) extends PersistentEvent
  private case class CannotBlockFunds(exchangeId: ExchangeId) extends PersistentEvent
  private case object CancelledOrder extends PersistentEvent
}
