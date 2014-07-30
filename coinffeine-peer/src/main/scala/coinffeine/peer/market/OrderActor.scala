package coinffeine.peer.market

import scala.concurrent.Future
import scala.util.{Failure, Success}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.google.bitcoin.core.NetworkParameters
import com.typesafe.config.Config

import coinffeine.common.akka.AskPattern
import coinffeine.model.bitcoin.KeyPair
import coinffeine.model.currency.{FiatAmount, FiatCurrency}
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.peer.api.event.{OrderSubmittedEvent, OrderUpdatedEvent}
import coinffeine.peer.bitcoin.WalletActor.{CreateKeyPair, KeyPairCreated}
import coinffeine.peer.event.EventProducer
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.market.OrderActor.{CancelOrder, Initialize, RetrieveStatus}
import coinffeine.peer.market.SubmissionSupervisor.{InMarket, KeepSubmitting, Offline, StopSubmitting}
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.ReceiveMessage
import coinffeine.protocol.messages.brokerage.OrderMatch

class OrderActor(exchangeActorProps: Props, network: NetworkParameters, intermediateSteps: Int)
  extends Actor with ActorLogging {

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

    def start(): Unit = {
      log.info(s"Order actor initialized for ${init.order.id} using $brokerId as broker")
      messageGateway ! MessageGateway.Subscribe {
        case ReceiveMessage(orderMatch: OrderMatch, `brokerId`) =>
          orderMatch.orderId == currentOrder.id
        case _ => false
      }

      currentOrder = currentOrder.withStatus(OfflineOrder)
      produceEvent(OrderSubmittedEvent(currentOrder))
      init.submissionSupervisor ! KeepSubmitting(OrderBookEntry(currentOrder))
      context.become(manageOrder)
    }

    private val manageOrder: Receive = {
      case InMarket(order) if orderBookEntryMatches(order) =>
        updateOrderStatus(InMarketOrder)

      case Offline(order) if orderBookEntryMatches(order) =>
        updateOrderStatus(OfflineOrder)

      case CancelOrder =>
        log.info(s"Order actor requested to cancel order ${currentOrder.id}")
        submissionSupervisor ! StopSubmitting(currentOrder.id)
        // TODO: determine the cancellation reason
        updateOrderStatus(CancelledOrder("unknown reason"))

      case RetrieveStatus =>
        log.debug(s"Order actor requested to retrieve status for ${currentOrder.id}")
        sender() ! currentOrder

      case ReceiveMessage(orderMatch: OrderMatch, _) =>
        log.info(s"Order actor received a match for ${currentOrder.id} " +
          s"with exchange ${orderMatch.exchangeId} and counterpart ${orderMatch.counterpart}")
        init.submissionSupervisor ! StopSubmitting(orderMatch.orderId)
        val newExchange = buildExchange(orderMatch)
        updateExchangeInOrder(newExchange)
        startExchange(newExchange)

      case ExchangeActor.ExchangeProgress(exchange) =>
        log.debug(s"Order actor received progress for exchange ${exchange.id}: ${exchange.progress}")
        updateExchangeInOrder(exchange)

      case ExchangeActor.ExchangeSuccess(exchange) =>
        log.debug(s"Order actor received success for exchange ${exchange.id}")
        currentOrder = currentOrder.withStatus(CompletedOrder)
        updateExchangeInOrder(exchange)
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
        orderMatch.exchangeId,
        amounts,
        Exchange.Parameters(orderMatch.lockTime, network),
        peerIds = null,
        brokerId
      )
    }

    private def spawnExchange(exchange: Exchange[FiatCurrency],
                              userInfo: Exchange.PeerInfo): Unit = {
      context.actorOf(exchangeActorProps, exchange.id.value) ! ExchangeActor.StartExchange(
        exchange = exchange,
        role,
        user = userInfo,
        wallet = null,
        paymentProcessor,
        messageGateway,
        bitcoinPeer
      )
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

    private def updateExchangeInOrder(exchange: Exchange[FiatCurrency]): Unit = {
      currentOrder = currentOrder.withExchange(exchange)
      produceEvent(OrderUpdatedEvent(currentOrder))
    }

    private def updateOrderStatus(newStatus: OrderStatus): Unit = {
      currentOrder = currentOrder.withStatus(newStatus)
      produceEvent(OrderUpdatedEvent(currentOrder))

    }

    private def orderBookEntryMatches(entry: OrderBookEntry[FiatAmount]): Boolean =
      entry.id == currentOrder.id && entry.amount == currentOrder.amount
  }
}

object OrderActor {

  case class Initialize(order: Order[FiatCurrency],
                        submissionSupervisor: ActorRef,
                        eventChannel: ActorRef,
                        messageGateway: ActorRef,
                        paymentProcessor: ActorRef,
                        bitcoinPeer: ActorRef,
                        wallet: ActorRef,
                        brokerId: PeerId)

  case object CancelOrder

  /** Ask for order status. To be replied with an [[Order]]. */
  case object RetrieveStatus

  def props(exchangeActorProps: Props, config: Config, network: NetworkParameters): Props = {
    val intermediateSteps = config.getInt("coinffeine.hardcoded.intermediateSteps")
    Props(new OrderActor(exchangeActorProps, network, intermediateSteps))
  }
}
