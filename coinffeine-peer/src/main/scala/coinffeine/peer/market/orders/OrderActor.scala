package coinffeine.peer.market.orders

import akka.actor._
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
    controllerFactory: (OrderPublication[C], FundsBlocker) => OrderController[C],
    delegates: OrderActor.Delegates[C],
    collaborators: OrderActor.Collaborators) extends Actor with ActorLogging {

  import coinffeine.peer.market.orders.OrderActor._

  private val orderId = initialOrder.id
  private val currency = initialOrder.price.currency
  private val fundsBlocker = delegates.delegatedFundsBlocking()
  private val publisher = new DelegatedPublication(
    initialOrder.id, initialOrder.orderType, initialOrder.price, collaborators.submissionSupervisor)
  private val order = controllerFactory(publisher, fundsBlocker)

  override def preStart(): Unit = {
    log.info("Order actor initialized for {}", orderId)
    subscribeToOrderMatches()
    subscribeToOrderChanges()
  }

  override def receive = publisher.receiveSubmissionEvents orElse fundsBlocker.blockingFunds orElse {

    case ReceiveMessage(message: OrderMatch[_], _) if message.currency == currency =>
      order.acceptOrderMatch(message.asInstanceOf[OrderMatch[C]])

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

  private def subscribeToOrderMatches(): Unit = {
    collaborators.gateway ! MessageGateway.Subscribe.fromBroker {
      case orderMatch: OrderMatch[_] if orderMatch.orderId == orderId &&
        orderMatch.currency == currency =>
    }
  }

  private def subscribeToOrderChanges(): Unit = {
    order.addListener(new OrderController.Listener[C] {
      override def onProgress(prevProgress: Double, newProgress: Double): Unit = {
        log.debug("Order {} progressed from {}% to {}%",
          orderId, (100 * prevProgress).formatted("%5.2f"), (100 * newProgress).formatted("%5.2f"))
      }

      override def onStatusChanged(oldStatus: OrderStatus, newStatus: OrderStatus): Unit = {
        log.info("Order {} status changed from {} to {}", orderId, oldStatus, newStatus)
      }

      override def onOrderMatchResolution(orderMatch: OrderMatch[C],
                                          result: MatchResult[C]): Unit = {
        result match {
          case MatchAccepted(newExchange) => acceptMatch(newExchange)
          case MatchRejected(cause) => rejectOrderMatch(cause, orderMatch)
          case MatchAlreadyAccepted(oldExchange) =>
            log.debug("Received order match for the already accepted exchange {}", oldExchange.id)
        }
      }
    })
  }

  private def rejectOrderMatch(cause: String, rejectedMatch: OrderMatch[C]): Unit = {
    log.info("Rejecting match for {} against counterpart {}: {}",
      orderId, rejectedMatch.counterpart, cause)
    val rejection = ExchangeRejection(rejectedMatch.exchangeId, cause)
    collaborators.gateway ! ForwardMessage(rejection, BrokerId)
  }

  private def acceptMatch(newExchange: NonStartedExchange[C]): Unit = {
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
      (publisher, funds) => new OrderController(
        amountsCalculator, network, order, coinffeineProperties, publisher, funds),
      delegates,
      collaborators
    ))
  }
}
