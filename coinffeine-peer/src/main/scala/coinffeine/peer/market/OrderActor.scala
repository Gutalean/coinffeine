package coinffeine.peer.market

import scala.concurrent.Future
import scala.util.{Failure, Success}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.google.bitcoin.core.NetworkParameters
import com.typesafe.config.Config

import coinffeine.common.akka.{AskPattern, ServiceRegistry}
import coinffeine.model.bitcoin.KeyPair
import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.event.{OrderProgressedEvent, OrderStatusChangedEvent, OrderSubmittedEvent}
import coinffeine.model.exchange.Exchange.BlockedFunds
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.peer.amounts.ExchangeAmountsCalculator
import coinffeine.peer.bitcoin.WalletActor
import coinffeine.peer.event.EventPublisher
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.market.OrderActor._
import coinffeine.peer.market.SubmissionSupervisor.{InMarket, KeepSubmitting, Offline, StopSubmitting}
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.ReceiveMessage
import coinffeine.protocol.messages.brokerage.OrderMatch

class OrderActor(exchangeActorProps: Props,
                 orderFundsActorProps: Props,
                 network: NetworkParameters,
                 intermediateSteps: Int,
                 amountsCalculator: ExchangeAmountsCalculator)
  extends Actor with ActorLogging with EventPublisher {

  import context.dispatcher

  override def receive: Receive = {
    case init @ Initialize(_, _, _, _, _, _) => new InitializedOrderActor(init).start()
  }

  private class InitializedOrderActor[C <: FiatCurrency](init: Initialize[C]) {
    import init.{order => _, _}

    private val role = init.order.orderType match {
      case Bid => BuyerRole
      case Ask => SellerRole
    }

    private var currentOrder = init.order
    private var blockedFunds: Option[BlockedFunds] = None
    private val fundsActor = context.actorOf(orderFundsActorProps, "funds")
    private val messageGateway = new ServiceRegistry(registry)
      .eventuallyLocate(MessageGateway.ServiceId)

    def start(): Unit = {
      log.info("Order actor initialized for {}", init.order.id)
      subscribeToMessages()
      blockFunds()
      startWithOrderStatus(StalledOrder(BlockingFundsMessage))
      log.warning(s"${currentOrder.id} is stalled until enough funds are available".capitalize)
      context.become(stalled)
    }

    private def subscribeToMessages(): Unit = {
      messageGateway ! MessageGateway.SubscribeToBroker {
        case orderMatch: OrderMatch if orderMatch.orderId == currentOrder.id &&
          orderMatch.price.currency == init.order.fiatAmount.currency =>
      }
    }

    private def blockFunds(): Unit = {
      val (fiatToBlock, bitcoinToBlock) = amountsCalculator.amountsFor(currentOrder)
      fundsActor ! OrderFundsActor.BlockFunds(fiatToBlock, bitcoinToBlock, wallet, paymentProcessor)
    }

    private def stalled: Receive = running orElse {
      case OrderFundsActor.AvailableFunds(availableBlockedFunds) =>
        blockedFunds = Some(availableBlockedFunds)
        log.info(s"{} received available funds {}. Moving to offline status",
          currentOrder.id, availableBlockedFunds)
        updateOrderStatus(OfflineOrder)
        submissionSupervisor ! KeepSubmitting(OrderBookEntry(currentOrder))
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
        init.submissionSupervisor ! StopSubmitting(orderMatch.orderId)
        updateOrderStatus(InProgressOrder)
        val newExchange = buildExchange(orderMatch)
        updateExchangeInOrder(newExchange)
        startExchange(newExchange)
        context.become(exchanging)
    }

    private def exchanging: Receive = running orElse availableFunds orElse {
      case ExchangeActor.ExchangeProgress(exchange: AnyExchange[C]) =>
        log.debug("Order actor received progress for {}: {}", exchange.id, exchange.progress)
        updateExchangeInOrder(exchange)

      case ExchangeActor.ExchangeSuccess(exchange: AnyExchange[C]) =>
        log.debug("Order actor received success for {}", exchange.id)
        updateExchangeInOrder(exchange)
        terminate(CompletedOrder)
    }

    private def availableFunds: Receive = {
      case OrderFundsActor.UnavailableFunds =>
        updateOrderStatus(StalledOrder(NoFundsMessage))
        submissionSupervisor ! StopSubmitting(currentOrder.id)
        log.warning("${} is stalled due to unavailable funds", currentOrder.id)
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

    private def terminate(finalStatus: OrderStatus): Unit = {
      updateOrderStatus(finalStatus)
      submissionSupervisor ! StopSubmitting(currentOrder.id)
      fundsActor ! OrderFundsActor.UnblockFunds
      context.become(Map.empty)
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
          init.submissionSupervisor ! KeepSubmitting(OrderBookEntry(currentOrder))
      }
    }

    private def buildExchange(orderMatch: OrderMatch): NonStartedExchange[C] = {
      val fiatAmount = orderMatch.price * currentOrder.amount.value
      val amounts = Exchange.Amounts(
        currentOrder.amount, fiatAmount.asInstanceOf[CurrencyAmount[C]],
        Exchange.StepBreakdown(intermediateSteps))
      Exchange.notStarted(
        id = orderMatch.exchangeId,
        role = role,
        counterpartId = orderMatch.counterpart,
        amounts = amounts,
        blockedFunds = blockedFunds.get,
        parameters = Exchange.Parameters(orderMatch.lockTime, network)
      )
    }

    private def spawnExchange(exchange: NonStartedExchange[C], user: Exchange.PeerInfo): Unit = {
      context.actorOf(exchangeActorProps, exchange.id.value) ! ExchangeActor.StartExchange(
        exchange, user, wallet, paymentProcessor, registry, bitcoinPeer)
    }

    private def createFreshKeyPair(): Future[KeyPair] = AskPattern(
      to = wallet,
      request = WalletActor.CreateKeyPair,
      errorMessage = "Cannot get a fresh key pair"
    ).withImmediateReply[WalletActor.KeyPairCreated]().map(_.keyPair)

    private def retrievePaymentProcessorId(): Future[AccountId] = AskPattern(
      to = paymentProcessor,
      request = PaymentProcessorActor.RetrieveAccountId,
      errorMessage = "Cannot retrieve the user account id"
    ).withImmediateReply[PaymentProcessorActor.RetrievedAccountId]().map(_.id)

    private def startWithOrderStatus(status: OrderStatus): Unit = {
      currentOrder = currentOrder.withStatus(status)
      publishEvent(OrderSubmittedEvent(currentOrder))
    }

    private def updateExchangeInOrder(exchange: AnyExchange[C]): Unit = {
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
}

object OrderActor {

  val BlockingFundsMessage = "blocking funds"
  val NoFundsMessage = "no funds available for order"

  case class Initialize[C <: FiatCurrency](order: Order[C],
                                           submissionSupervisor: ActorRef,
                                           registry: ActorRef,
                                           paymentProcessor: ActorRef,
                                           bitcoinPeer: ActorRef,
                                           wallet: ActorRef)

  case class CancelOrder(reason: String)

  /** Ask for order status. To be replied with an [[Order]]. */
  case object RetrieveStatus

  def props(exchangeActorProps: Props,
            config: Config,
            network: NetworkParameters,
            amountsCalculator: ExchangeAmountsCalculator): Props = {
    val intermediateSteps = config.getInt("coinffeine.hardcoded.intermediateSteps")
    Props(new OrderActor(
      exchangeActorProps,
      OrderFundsActor.props,
      network,
      intermediateSteps,
      amountsCalculator))
  }
}
