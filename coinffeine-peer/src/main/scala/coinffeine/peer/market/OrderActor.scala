package coinffeine.peer.market

import scala.concurrent.Future
import scala.util.{Failure, Success}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.google.bitcoin.core.NetworkParameters
import com.typesafe.config.Config

import coinffeine.common.akka.AskPattern
import coinffeine.model.bitcoin.KeyPair
import coinffeine.model.currency.{FiatAmount, FiatCurrency}
import coinffeine.model.exchange.Exchange.BlockedFunds
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.peer.api.event.{OrderProgressedEvent, OrderStatusChangedEvent, OrderSubmittedEvent}
import coinffeine.peer.bitcoin.WalletActor.{CreateKeyPair, KeyPairCreated}
import coinffeine.peer.event.EventProducer
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
                 intermediateSteps: Int) extends Actor with ActorLogging {

  import context.dispatcher

  override def receive: Receive = {
    case init: Initialize =>
      new InitializedOrderActor(init).start()
  }

  private class InitializedOrderActor(init: Initialize) extends EventProducer(init.eventChannel) {
    import init.{order => _, _}

    private val role = init.order.orderType match {
      case Bid => BuyerRole
      case Ask => SellerRole
    }

    private var currentOrder = init.order
    private var blockedFunds: Option[BlockedFunds] = None
    private val fundsActor = context.actorOf(orderFundsActorProps, "funds")

    def start(): Unit = {
      log.info(s"Order actor initialized for ${init.order.id} using $brokerId as broker")
      subscribeToMessages()
      blockFunds()
      startWithOrderStatus(StalledOrder(BlockingFundsMessage))
      log.warning(s"${currentOrder.id} is stalled until enough funds are available".capitalize)
      context.become(stalled)
    }

    private def subscribeToMessages(): Unit = {
      messageGateway ! MessageGateway.Subscribe {
        case ReceiveMessage(orderMatch: OrderMatch, `brokerId`) =>
          orderMatch.orderId == currentOrder.id
        case _ => false
      }
    }

    private def blockFunds(): Unit = {
      val fiatToBlock = currentOrder.orderType match {
        case Bid =>
          log.info("{} is bidding, blocking {} in payment processor",
            currentOrder.id, currentOrder.fiatAmount)
          currentOrder.fiatAmount
        case Ask =>
          log.info("{} is asking, no funds blocking in payment processor required", currentOrder.id)
          currentOrder.fiatAmount.currency.Zero
      }
      val bitcoinToBlock = currentOrder.amount * role.select(ProportionOfBitcoinToBlock)
      fundsActor ! OrderFundsActor.BlockFunds(fiatToBlock, bitcoinToBlock, wallet, paymentProcessor)
    }

    private def stalled: Receive = running orElse {
      case OrderFundsActor.AvailableFunds(availableBlockedFunds) =>
        blockedFunds = Some(availableBlockedFunds)
        log.info(s"{} received available funds {}. Moving to offline status",
          currentOrder.id, availableBlockedFunds)
        updateOrderStatus(OfflineOrder)
        submissionSupervisor ! KeepSubmitting(OrderBookEntry(currentOrder))
        context.become(offline)
    }

    private def offline: Receive = running orElse availableFunds orElse {
      case InMarket(order) if orderBookEntryMatches(order) =>
        updateOrderStatus(InMarketOrder)
        context.become(waitingForMatch)
    }

    private def waitingForMatch: Receive = running orElse availableFunds orElse {
      case Offline(order) if orderBookEntryMatches(order) =>
        updateOrderStatus(OfflineOrder)
        context.become(offline)

      case ReceiveMessage(orderMatch: OrderMatch, _) =>
        log.info(s"Order actor received a match for ${currentOrder.id} " +
          s"with exchange ${orderMatch.exchangeId} and counterpart ${orderMatch.counterpart}")
        init.submissionSupervisor ! StopSubmitting(orderMatch.orderId)
        val newExchange = buildExchange(orderMatch)
        updateExchangeInOrder(newExchange)
        startExchange(newExchange)
        context.become(exchanging)
    }

    private def exchanging: Receive = running orElse availableFunds orElse {
      case ExchangeActor.ExchangeProgress(exchange) =>
        log.debug(s"Order actor received progress for exchange ${exchange.id}: ${exchange.progress}")
        updateExchangeInOrder(exchange)

      case ExchangeActor.ExchangeSuccess(exchange) =>
        log.debug(s"Order actor received success for exchange ${exchange.id}")
        updateOrderStatus(CompletedOrder)
        updateExchangeInOrder(exchange)
        context.become(terminated)
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
        log.info(s"Order actor requested to cancel order ${currentOrder.id}")
        submissionSupervisor ! StopSubmitting(currentOrder.id)
        updateOrderStatus(CancelledOrder(reason))
        context.become(terminated)
    }

    private def terminated: Receive = {
      case _ => log.info(s"${currentOrder.id} is terminated")
    }

    private def startExchange(newExchange: Exchange[FiatCurrency]): Unit = {
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

    private def buildExchange(orderMatch: OrderMatch): Exchange[FiatCurrency] = {
      val fiatAmount = orderMatch.price * currentOrder.amount.value
      val amounts = Exchange.Amounts(
        currentOrder.amount, fiatAmount, Exchange.StepBreakdown(intermediateSteps))
      NonStartedExchange(
        id = orderMatch.exchangeId,
        role = role,
        counterpartId = orderMatch.counterpart,
        amounts = amounts,
        blockedFunds = blockedFunds.get,
        parameters = Exchange.Parameters(orderMatch.lockTime, network),
        brokerId = brokerId
      )
    }

    private def spawnExchange(exchange: Exchange[FiatCurrency], user: Exchange.PeerInfo): Unit = {
      context.actorOf(exchangeActorProps, exchange.id.value) ! ExchangeActor.StartExchange(
        exchange, role, user, wallet, paymentProcessor, messageGateway, bitcoinPeer)
    }

    private def createFreshKeyPair(): Future[KeyPair] =
      AskPattern(to = wallet, request = CreateKeyPair, errorMessage = "Cannot get a fresh key pair")
        .withImmediateReply[KeyPairCreated]()
        .map(_.keyPair)

    private def retrievePaymentProcessorId(): Future[AccountId] = AskPattern(
      to = paymentProcessor,
      request = PaymentProcessorActor.Identify,
      errorMessage = "Cannot retrieve payment processor id"
    ).withImmediateReply[PaymentProcessorActor.Identified]().map(_.id)

    private def startWithOrderStatus(status: OrderStatus): Unit = {
      currentOrder = currentOrder.withStatus(status)
      produceEvent(OrderSubmittedEvent(currentOrder))
    }

    private def updateExchangeInOrder(exchange: Exchange[FiatCurrency]): Unit = {
      val prevProgress = currentOrder.progress
      currentOrder = currentOrder.withExchange(exchange)
      val newProgress = currentOrder.progress
      produceEvent(OrderProgressedEvent(currentOrder.id, prevProgress, newProgress))
    }

    private def updateOrderStatus(newStatus: OrderStatus): Unit = {
      val prevStatus = currentOrder.status
      currentOrder = currentOrder.withStatus(newStatus)
      produceEvent(OrderStatusChangedEvent(currentOrder.id, prevStatus, newStatus))
    }

    private def orderBookEntryMatches(entry: OrderBookEntry[FiatAmount]): Boolean =
      entry.id == currentOrder.id && entry.amount == currentOrder.amount
  }
}

object OrderActor {

  /** Bitcoins to block as a proportion of the amount to be transferred */
  private val ProportionOfBitcoinToBlock = Both[BigDecimal](buyer = 0.2, seller = 1.1)

  val BlockingFundsMessage = "blocking funds"
  val NoFundsMessage = "no funds available for order"

  case class Initialize(order: Order[FiatCurrency],
                        submissionSupervisor: ActorRef,
                        eventChannel: ActorRef,
                        messageGateway: ActorRef,
                        paymentProcessor: ActorRef,
                        bitcoinPeer: ActorRef,
                        wallet: ActorRef,
                        brokerId: PeerId)

  case class CancelOrder(reason: String)

  /** Ask for order status. To be replied with an [[Order]]. */
  case object RetrieveStatus

  def props(exchangeActorProps: Props, config: Config, network: NetworkParameters): Props = {
    val intermediateSteps = config.getInt("coinffeine.hardcoded.intermediateSteps")
    Props(new OrderActor(exchangeActorProps, OrderFundsActor.props, network, intermediateSteps))
  }
}
