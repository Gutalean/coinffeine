package coinffeine.peer.market.orders

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Try, Failure, Success}

import akka.actor._
import akka.pattern._
import com.google.bitcoin.core.NetworkParameters

import coinffeine.common.akka.AskPattern
import coinffeine.model.bitcoin.KeyPair
import coinffeine.model.currency._
import coinffeine.model.event.{OrderProgressedEvent, OrderStatusChangedEvent, OrderSubmittedEvent}
import coinffeine.model.exchange.Exchange.CannotStartHandshake
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.BrokerId
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.peer.bitcoin.WalletActor
import coinffeine.peer.event.EventPublisher
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.exchange.ExchangeActor.{ExchangeActorProps, ExchangeToStart}
import coinffeine.peer.market.orders.controller._
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, ReceiveMessage}
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.messages.handshake.ExchangeRejection

class OrderActor[C <: FiatCurrency](initialOrder: Order[C],
                                    amountsCalculator: AmountsCalculator,
                                    controllerFactory: (OrderPublication[C], OrderFunds) => OrderController[C],
                                    delegates: OrderActor.Delegates[C],
                                    collaborators: OrderActor.Collaborators)
  extends Actor with ActorLogging with EventPublisher {

  import OrderActor._
  import context.dispatcher

  private case class SpawnExchange(exchange: NonStartedExchange[C], user: Try[Exchange.PeerInfo])

  private val orderId = initialOrder.id
  private val requiredFunds: (CurrencyAmount[C], BitcoinAmount) = {
    val amounts = amountsCalculator.exchangeAmountsFor(initialOrder)
    val role = Role.fromOrderType(initialOrder.orderType)
    (role.select(amounts.fiatRequired), role.select(amounts.bitcoinRequired))
  }
  private val fundsBlocking =
    new DelegatedOrderFunds(delegates.orderFundsActor, requiredFunds._1, requiredFunds._2)
  private val publisher = new DelegatedPublication(
    initialOrder.id, initialOrder.orderType, initialOrder.price, collaborators.submissionSupervisor)
  private val order = controllerFactory(publisher, fundsBlocking)

  override def preStart(): Unit = {
    log.info("Order actor initialized for {}", orderId)
    subscribeToOrderMatches()
    subscribeToOrderChanges()
    publishEvent(OrderSubmittedEvent(order.view))
  }

  override def receive = publisher.receiveSubmissionEvents orElse
    fundsBlocking.managingFundsAvailability orElse {

    case RetrieveStatus =>
      log.debug("Order actor requested to retrieve status for {}", orderId)
      sender() ! order.view

    case ReceiveMessage(message: OrderMatch[_], _) if message.currency == initialOrder.price.currency =>
      val orderMatch = message.asInstanceOf[OrderMatch[C]]
      order.acceptOrderMatch(orderMatch) match {
        case MatchAccepted(newExchange) => startExchange(newExchange)
        case MatchRejected(cause) => rejectOrderMatch(cause, orderMatch)
        case MatchAlreadyAccepted(oldExchange) =>
          log.debug("Received order match for the already accepted exchange {}", oldExchange)
      }

    case SpawnExchange(exchange, Success(userInfo)) =>
       spawnExchange(exchange, userInfo)

    case SpawnExchange(exchange, Failure(cause)) =>
      log.error(cause, "Cannot start exchange {} for {} order", exchange.id, orderId)
      order.completeExchange(exchange.cancel(CannotStartHandshake(cause)))

    case CancelOrder(reason) =>
      log.info("Cancelling order {}", orderId)
      order.cancel(reason)

    case ExchangeActor.ExchangeUpdate(exchange: AnyStateExchange[C]) =>
      log.debug("Order actor received update for {}: {}", exchange.id, exchange.progress)
      order.updateExchange(exchange)

    case ExchangeActor.ExchangeSuccess(exchange: SuccessfulExchange[C]) =>
      order.completeExchange(exchange)

    case ExchangeActor.ExchangeFailure(exchange: FailedExchange[C]) =>
      order.completeExchange(exchange)
  }

  private def subscribeToOrderMatches(): Unit = {
    collaborators.gateway ! MessageGateway.Subscribe.fromBroker {
      case orderMatch: OrderMatch[_] if orderMatch.orderId == orderId &&
        orderMatch.currency == order.view.price.currency =>
    }
  }

  private def subscribeToOrderChanges(): Unit = {
    order.addListener(new OrderController.Listener[C] {
      override def onProgress(prevProgress: Double, newProgress: Double): Unit = {
        publishEvent(OrderProgressedEvent(orderId, prevProgress, newProgress))
      }

      override def onStatusChanged(oldStatus: OrderStatus, newStatus: OrderStatus): Unit = {
        log.info("Order {} status changed from {} to {}", orderId, oldStatus, newStatus)
        publishEvent(OrderStatusChangedEvent(orderId, oldStatus, newStatus))
      }

      override def onFinish(finalStatus: OrderStatus): Unit = {
        fundsBlocking.release()
      }
    })
  }

  private def rejectOrderMatch(cause: String, rejectedMatch: OrderMatch[C]): Unit = {
    log.info("Rejecting match for {} against counterpart {}: {}",
      orderId, rejectedMatch.counterpart, cause)
    val rejection = ExchangeRejection(rejectedMatch.exchangeId, cause)
    collaborators.gateway ! ForwardMessage(rejection, BrokerId)
  }

  private def startExchange(newExchange: NonStartedExchange[C]): Unit = {
    log.info("Accepting match for {} against counterpart {} identified as {}",
      orderId, newExchange.counterpartId, newExchange.id)
    (for {
      keyPair <- createFreshKeyPair()
      paymentProcessorId <- retrievePaymentProcessorId()
    } yield Exchange.PeerInfo(paymentProcessorId, keyPair))
      .map(Success.apply)
      .recover { case NonFatal(ex) => Failure(ex) }
      .map(SpawnExchange(newExchange, _))
      .pipeTo(self)
  }

  private def spawnExchange(exchange: NonStartedExchange[C], user: Exchange.PeerInfo): Unit = {
    val props = delegates.exchangeActor(
      ExchangeActor.ExchangeToStart(exchange, user), resultListener = self)
    context.actorOf(props, exchange.id.value)
  }

  private def createFreshKeyPair(): Future[KeyPair] = AskPattern(
    to = collaborators.wallet,
    request = WalletActor.CreateKeyPair,
    errorMessage = "Cannot get a fresh key pair"
  ).withImmediateReply[WalletActor.KeyPairCreated]().map(_.keyPair)

  private def retrievePaymentProcessorId(): Future[AccountId] = AskPattern(
    to = collaborators.paymentProcessor,
    request = PaymentProcessorActor.RetrieveAccountId,
    errorMessage = "Cannot retrieve the user account id"
  ).withImmediateReply[PaymentProcessorActor.RetrievedAccountId]().map(_.id)
}

object OrderActor {
  case class Collaborators(wallet: ActorRef,
                           paymentProcessor: ActorRef,
                           submissionSupervisor: ActorRef,
                           gateway: ActorRef,
                           bitcoinPeer: ActorRef)

  trait Delegates[C <: FiatCurrency] {
    def exchangeActor(exchange: ExchangeActor.ExchangeToStart[C], resultListener: ActorRef): Props
    def orderFundsActor: Props
  }

  case class CancelOrder(reason: String)

  /** Ask for order status. To be replied with an [[Order]]. */
  case object RetrieveStatus

  def props[C <: FiatCurrency](exchangeActorProps: ExchangeActorProps,
                               network: NetworkParameters,
                               amountsCalculator: AmountsCalculator,
                               order: Order[C],
                               collaborators: Collaborators): Props = {
    val delegates = new Delegates[C] {
      override def exchangeActor(exchange: ExchangeToStart[C], resultListener: ActorRef) = {
        import collaborators._
        exchangeActorProps(exchange, ExchangeActor.Collaborators(
          wallet, paymentProcessor, gateway, bitcoinPeer, resultListener))
      }
      override def orderFundsActor = OrderFundsActor.props(
        collaborators.wallet, collaborators.paymentProcessor)
    }
    Props(new OrderActor[C](
      order,
      amountsCalculator,
      (publisher, funds) => new OrderController(amountsCalculator, network, order, publisher, funds),
      delegates,
      collaborators
    ))
  }
}
