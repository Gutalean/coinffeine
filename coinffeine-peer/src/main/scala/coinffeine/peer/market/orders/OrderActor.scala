package coinffeine.peer.market.orders

import akka.actor._
import akka.persistence.PersistentActor
import org.bitcoinj.core.NetworkParameters

import coinffeine.model.currency._
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.{BrokerId, MutableCoinffeineNetworkProperties}
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.market.orders.controller._
import coinffeine.peer.market.orders.funds.{DelegatedFundsBlocker, FundsBlockerActor}
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
  extends PersistentActor with ActorLogging with OrderPublisher.Listener with FundsBlocker.Listener {

  import OrderActor._

  private val orderId = initialOrder.id
  override val persistenceId: String = s"order-${orderId.value}"
  private val currency = initialOrder.price.currency
  private val fundsBlocker = delegates.delegatedFundsBlocking()
  private val publisher = new OrderPublisher[C](collaborators.submissionSupervisor, this)
  private var pendingOrderMatch: Option[OrderMatch[C]] = None

  override def preStart(): Unit = {
    log.info("Order actor initialized for {}", orderId)
    subscribeToOrderMatches()
    subscribeToOrderChanges()
    super.preStart()
  }

  override def receiveRecover: Receive = Map.empty

  override def receiveCommand = publisher.receiveSubmissionEvents orElse fundsBlocker.blockingFunds orElse {

    case ReceiveMessage(orderMatch: OrderMatch[_], _) if pendingOrderMatch.nonEmpty =>
      rejectOrderMatch("Accepting other match", orderMatch)

    case ReceiveMessage(message: OrderMatch[_], _) if message.currency == currency =>
      val orderMatch = message.asInstanceOf[OrderMatch[C]]
      order.shouldAcceptOrderMatch(orderMatch) match {
        case MatchAccepted(requiredFunds) =>
          pendingOrderMatch = Some(orderMatch)
          fundsBlocker.blockFunds(orderMatch.exchangeId, requiredFunds, this)
        case MatchRejected(cause) =>
          rejectOrderMatch(cause, orderMatch)
        case MatchAlreadyAccepted(oldExchange) =>
          log.debug("Received order match for the already accepted exchange {}", oldExchange.id)
      }

    case CancelOrder(reason) =>
      log.info("Cancelling order {}", orderId)
      order.cancel(reason)

    case ExchangeActor.ExchangeUpdate(exchange) if exchange.currency == currency =>
      log.debug("Order actor received update for {}: {}", exchange.id, exchange.progress)
      order.updateExchange(exchange.asInstanceOf[AnyStateExchange[C]])

    case ExchangeActor.ExchangeSuccess(exchange) if exchange.currency == currency =>
      order.completeExchange(exchange.asInstanceOf[SuccessfulExchange[C]])

    case ExchangeActor.ExchangeFailure(exchange) if exchange.currency == currency =>
      order.completeExchange(exchange.asInstanceOf[FailedExchange[C]])
  }

  override def inMarket(): Unit = { order.becomeInMarket() }
  override def offline(): Unit = { order.becomeOffline() }

  override def fundsBlocked(): Unit = {
    spawnExchange(order.acceptOrderMatch(pendingOrderMatch.get))
    pendingOrderMatch = None
  }

  override def cannotBlockFunds(cause: Throwable): Unit = {
    rejectOrderMatch("Cannot block funds", pendingOrderMatch.get)
    pendingOrderMatch = None
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
        if (newOrder.status != oldOrder.status) {
          log.info("Order {} has now {} status", orderId, newOrder.status)
        }
        if (newOrder.progress != oldOrder.progress) {
          log.debug("Order {} progress: {}%", orderId, (100 * newOrder.progress).formatted("%5.2f"))
        }
        coinffeineProperties.orders.set(newOrder.id, newOrder)
      }

      override def keepInMarket(): Unit = {
        publisher.keepPublishing(order.view.pendingOrderBookEntry)
      }

      override def keepOffMarket(): Unit = {
        publisher.stopPublishing()
      }
    })
  }

  private def rejectOrderMatch(cause: String, rejectedMatch: OrderMatch[_]): Unit = {
    log.info("Rejecting match for {} against counterpart {}: {}",
      orderId, rejectedMatch.counterpart, cause)
    val rejection = ExchangeRejection(rejectedMatch.exchangeId, cause)
    collaborators.gateway ! ForwardMessage(rejection, BrokerId)
  }

  private def spawnExchange(newExchange: NonStartedExchange[C]): Unit = {
    log.info("Accepting match for {} against counterpart {} identified as {}",
      orderId, newExchange.counterpartId, newExchange.id)
    context.actorOf(delegates.exchangeActor(newExchange), newExchange.id.value)
  }
}

object OrderActor {
  case class Collaborators(wallet: ActorRef,
                           paymentProcessor: ActorRef,
                           submissionSupervisor: ActorRef,
                           gateway: ActorRef,
                           bitcoinPeer: ActorRef,
                           blockchain: ActorRef)

  trait OrderFundsBlocker extends FundsBlocker {
    def blockingFunds: Actor.Receive
  }

  trait Delegates[C <: FiatCurrency] {
    def exchangeActor(exchange: NonStartedExchange[C])(implicit context: ActorContext): Props
    def delegatedFundsBlocking()(implicit context: ActorContext): OrderFundsBlocker
  }

  case class CancelOrder(reason: String)

  def props[C <: FiatCurrency](exchangeActorProps: (NonStartedExchange[C], ExchangeActor.Collaborators) => Props,
                               network: NetworkParameters,
                               amountsCalculator: AmountsCalculator,
                               order: Order[C],
                               coinffeineProperties: MutableCoinffeineNetworkProperties,
                               collaborators: Collaborators): Props = {
    import collaborators._
    val delegates = new Delegates[C] {
      override def exchangeActor(exchange: NonStartedExchange[C])(implicit context: ActorContext) = {
        exchangeActorProps(exchange, ExchangeActor.Collaborators(
          wallet, paymentProcessor, gateway, bitcoinPeer, blockchain, context.self))
      }
      override def delegatedFundsBlocking()(implicit context: ActorContext) =
        new DelegatedFundsBlocker((id, requiredFunds) =>
          FundsBlockerActor.props(id, wallet, paymentProcessor, requiredFunds, context.self))
    }
    Props(new OrderActor[C](
      order,
      new OrderController(amountsCalculator, network, order),
      delegates,
      coinffeineProperties,
      collaborators
    ))
  }
}
