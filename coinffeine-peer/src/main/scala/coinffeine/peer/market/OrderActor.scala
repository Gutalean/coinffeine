package coinffeine.peer.market

import scala.concurrent.Future
import scala.util.{Failure, Success}

import akka.actor._
import com.google.bitcoin.core.NetworkParameters

import coinffeine.common.akka.{AskPattern, ServiceRegistry}
import coinffeine.model.bitcoin.KeyPair
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.event.{OrderProgressedEvent, OrderStatusChangedEvent, OrderSubmittedEvent}
import coinffeine.model.exchange.Exchange.BlockedFunds
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.BrokerId
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.peer.bitcoin.WalletActor
import coinffeine.peer.event.EventPublisher
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.exchange.ExchangeActor.ExchangeActorProps
import coinffeine.peer.market.OrderActor._
import coinffeine.peer.market.SubmissionSupervisor.{InMarket, KeepSubmitting, Offline, StopSubmitting}
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, ReceiveMessage}
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.messages.handshake.ExchangeRejection

class OrderActor[C <: FiatCurrency](exchangeActorProps: ExchangeActorProps,
                                    orderFundsActorProps: Props,
                                    network: NetworkParameters,
                                    amountsCalculator: AmountsCalculator,
                                    initialOrder: Order[C],
                                    collaborators: Collaborators)
  extends Actor with ActorLogging with EventPublisher {

  import context.dispatcher

  private val role = Role.fromOrderType(initialOrder.orderType)
  private var currentOrder = initialOrder
  private var blockedFunds: Option[BlockedFunds] = None
  private val fundsActor = context.actorOf(orderFundsActorProps, "funds")
  private val messageGateway = new ServiceRegistry(collaborators.registry)
    .eventuallyLocate(MessageGateway.ServiceId)

  override def preStart(): Unit = {
    log.info("Order actor initialized for {}", initialOrder.id)
    subscribeToMessages()
    blockFunds()
    startWithOrderStatus(StalledOrder(BlockingFundsMessage))
  }

  override def receive = stalled

  private def subscribeToMessages(): Unit = {
    messageGateway ! MessageGateway.Subscribe.fromBroker {
      case orderMatch: OrderMatch if orderMatch.orderId == currentOrder.id &&
        orderMatch.fiatAmount.currency == initialOrder.price.currency =>
    }
  }

  private def blockFunds(): Unit = {
    val amounts = amountsCalculator.exchangeAmountsFor(currentOrder)
    val fiatAmount = role.select(amounts.fiatRequired)
    val bitcoinAmount = role.select(amounts.bitcoinRequired)
    log.info("{} is stalled until enough funds are available {}", currentOrder.id,
      (fiatAmount, bitcoinAmount))
    fundsActor ! OrderFundsActor.BlockFunds(fiatAmount, bitcoinAmount)
  }

  private def stalled: Receive = running orElse rejectOrderMatches("Order is stalled") orElse {
    case OrderFundsActor.AvailableFunds(availableBlockedFunds) =>
      blockedFunds = Some(availableBlockedFunds)
      log.info("{} received available funds {}. Moving to offline status",
        currentOrder.id, availableBlockedFunds)
      updateOrderStatus(OfflineOrder)
      collaborators.submissionSupervisor ! KeepSubmitting(OrderBookEntry(currentOrder))
      context.become(waitingForMatch)
  }

  private def waitingForMatch: Receive = running orElse availableFunds orElse {
    case InMarket(order) if orderBookEntryMatches(order) =>
      updateOrderStatus(InMarketOrder)

    case Offline(order) if orderBookEntryMatches(order) =>
      updateOrderStatus(OfflineOrder)

    case ReceiveMessage(orderMatch: OrderMatch, _) =>
      log.info("Match for {} against counterpart {} identified as {}", currentOrder.id,
        orderMatch.counterpart, orderMatch.exchangeId)
      // TODO: check price to be in range
      collaborators.submissionSupervisor ! StopSubmitting(orderMatch.orderId)
      updateOrderStatus(InProgressOrder)
      val newExchange = buildExchange(orderMatch)
      updateExchangeInOrder(newExchange)
      startExchange(newExchange)
      context.become(exchanging)
  }

  private def exchanging: Receive = running orElse availableFunds orElse
    rejectOrderMatches("Exchange already in progress") orElse {
      case ExchangeActor.ExchangeProgress(exchange: AnyStateExchange[C]) =>
        log.debug("Order actor received progress for {}: {}", exchange.id, exchange.progress)
        updateExchangeInOrder(exchange)

      case ExchangeActor.ExchangeSuccess(exchange: AnyStateExchange[C]) =>
        log.debug("Order actor received success for {}", exchange.id)
        updateExchangeInOrder(exchange)
        terminate(CompletedOrder)
    }

  private def availableFunds: Receive = {
    case OrderFundsActor.UnavailableFunds =>
      updateOrderStatus(StalledOrder(NoFundsMessage))
      collaborators.submissionSupervisor ! StopSubmitting(currentOrder.id)
      log.warning("{} is stalled due to unavailable funds", currentOrder.id)
      context.become(stalled)
  }

  private def running: Receive = {
    case RetrieveStatus =>
      log.debug(s"Order actor requested to retrieve status for ${currentOrder.id}")
      sender() ! currentOrder

    case CancelOrder(reason) =>
      log.info("Cancelling order {}", currentOrder.id)
      terminate(CancelledOrder(reason))
  }

  private def rejectOrderMatches(errorMessage: String): Receive = {
    case ReceiveMessage(orderMatch: OrderMatch, _) =>
      if (currentOrder.exchanges.values.map(_.id).toSet.contains(orderMatch.exchangeId)) {
        log.debug("Received order match for the already accepted exchange {}",
          orderMatch.exchangeId)
      } else {
        val rejection = ExchangeRejection(orderMatch.exchangeId, errorMessage)
        messageGateway ! ForwardMessage(rejection, BrokerId)
      }
  }

  private def terminate(finalStatus: OrderStatus): Unit = {
    updateOrderStatus(finalStatus)
    collaborators.submissionSupervisor ! StopSubmitting(currentOrder.id)
    fundsActor ! OrderFundsActor.UnblockFunds
    context.become(rejectOrderMatches("Already finished"))
  }

  private def startExchange(newExchange: NonStartedExchange[C]): Unit = {
    val userInfoFuture = for {
      keyPair <- createFreshKeyPair()
      paymentProcessorId <- retrievePaymentProcessorId()
    } yield Exchange.PeerInfo(paymentProcessorId, keyPair)
    userInfoFuture.onComplete {
      case Success(userInfo) => spawnExchange(newExchange, userInfo)
      case Failure(cause) =>
        log.error(cause,
          s"Cannot start exchange ${newExchange.id} for ${currentOrder.id} order")
        collaborators.submissionSupervisor ! KeepSubmitting(OrderBookEntry(currentOrder))
    }
  }

  private def buildExchange(orderMatch: OrderMatch): NonStartedExchange[C] = {
    val amounts = amountsCalculator.exchangeAmountsFor(orderMatch).asInstanceOf[Exchange.Amounts[C]]
    Exchange.notStarted(
      id = orderMatch.exchangeId,
      role = role,
      counterpartId = orderMatch.counterpart,
      amounts,
      blockedFunds = blockedFunds.get,
      parameters = Exchange.Parameters(orderMatch.lockTime, network)
    )
  }

  private def spawnExchange(exchange: NonStartedExchange[C], user: Exchange.PeerInfo): Unit = {
    import collaborators._
    val props = exchangeActorProps(
      ExchangeActor.ExchangeToStart(exchange, user),
      ExchangeActor.Collaborators(wallet, paymentProcessor, registry, bitcoinPeer, resultListener = self)
    )
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

  private def startWithOrderStatus(status: OrderStatus): Unit = {
    currentOrder = currentOrder.withStatus(status)
    publishEvent(OrderSubmittedEvent(currentOrder))
  }

  private def updateExchangeInOrder(exchange: AnyStateExchange[C]): Unit = {
    val prevProgress = currentOrder.progress
    currentOrder = currentOrder.withExchange(exchange)
    val newProgress = currentOrder.progress
    publishEvent(OrderProgressedEvent(currentOrder.id, prevProgress, newProgress))
  }

  private def updateOrderStatus(newStatus: OrderStatus): Unit = {
    val prevStatus = currentOrder.status
    currentOrder = currentOrder.withStatus(newStatus)
    publishEvent(OrderStatusChangedEvent(currentOrder.id, prevStatus, newStatus))
  }

  private def orderBookEntryMatches(entry: OrderBookEntry[_]): Boolean =
    entry.id == currentOrder.id && entry.amount == currentOrder.amount
}

object OrderActor {

  val BlockingFundsMessage = "blocking funds"
  val NoFundsMessage = "no funds available for order"

  case class Collaborators(wallet: ActorRef,
                           paymentProcessor: ActorRef,
                           submissionSupervisor: ActorRef,
                           registry: ActorRef,
                           bitcoinPeer: ActorRef)

  case class CancelOrder(reason: String)

  /** Ask for order status. To be replied with an [[Order]]. */
  case object RetrieveStatus

  def props(exchangeActorProps: ExchangeActorProps,
            network: NetworkParameters,
            amountsCalculator: AmountsCalculator,
            order: Order[_ <: FiatCurrency],
            collaborators: Collaborators): Props = {
    Props(new OrderActor(
      exchangeActorProps,
      OrderFundsActor.props(collaborators.wallet, collaborators.paymentProcessor),
      network,
      amountsCalculator,
      order,
      collaborators
    ))
  }
}
