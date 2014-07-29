package coinffeine.peer.payment.okpay

import java.net.URI
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor._
import akka.pattern._
import com.typesafe.config.Config

import coinffeine.model.currency.{FiatAmount, FiatCurrency}
import coinffeine.model.payment.PaymentProcessor._
import coinffeine.peer.api.event.FiatBalanceChangeEvent
import coinffeine.peer.event.EventProducer
import coinffeine.peer.payment.PaymentProcessorActor._
import coinffeine.peer.payment._

class OkPayProcessorActor(account: AccountId, client: OkPayClient, pollingInterval: FiniteDuration)
  extends Actor {

  import OkPayProcessorActor._
  import context.dispatcher

  override def receive: Receive = {
    case Initialize(eventChannel) =>
      new InitializedBehavior(eventChannel).start()
  }

  private var timer: Cancellable = _

  override def preStart(): Unit = {
    timer = context.system.scheduler.schedule(
      initialDelay = pollingInterval,
      interval = pollingInterval,
      receiver = self,
      message = PollBalance
    )
  }

  override def postStop(): Unit = {
    Option(timer).foreach(_.cancel())
  }

  private class InitializedBehavior(eventChannel: ActorRef) extends EventProducer(eventChannel) {

    private val blockedFunds = new BlockedFunds()

    def start(): Unit = {
      pollBalances()
      context.become(managePayments)
    }

    val managePayments: Receive = {
      case PaymentProcessorActor.Identify =>
        sender ! Identified(OkPayProcessorActor.Id)
      case pay: Pay[_] =>
        sendPayment(sender(), pay)
      case FindPayment(paymentId) =>
        findPayment(sender(), paymentId)
      case RetrieveBalance(currency) =>
        currentBalance(sender(), currency)
      case PollBalance =>
        pollBalances()
      case UpdatedBalances(balances) =>
        updateBlockedFunds(balances)
        for (balance <- balances) {
          produceEvent(FiatBalanceChangeEvent(balance))
        }
      case BlockFunds(amount, listener) =>
        sender() ! blockedFunds.block(amount, listener)
          .fold[BlockFundsResult](NotEnoughFunds)(FundsBlocked.apply)
      case UnblockFunds(fundsId) =>
        unblockFunds(fundsId)
    }

    private def sendPayment[C <: FiatCurrency](requester: ActorRef, pay: Pay[C]): Unit = {
      if (!blockedFunds.canUseFunds(pay.fundsId, pay.amount)) {
        requester ! PaymentFailed(pay,
          new PaymentProcessorException(s"${pay.amount} is not backed by funds ${pay.fundsId}"))
      } else {
        val paymentFuture = client.sendPayment(pay.to, pay.amount, pay.comment)
        paymentFuture.onComplete {
          case Success(payment) =>
            requester ! Paid(payment)
            blockedFunds.useFunds(pay.fundsId, pay.amount)
          case Failure(error) =>
            requester ! PaymentFailed(pay, error)
        }
        paymentFuture.onComplete(_ => pollBalances())
      }
    }

    private def findPayment(requester: ActorRef, paymentId: PaymentId): Unit = {
      client.findPayment(paymentId).onComplete {
        case Success(Some(payment)) => requester ! PaymentFound(payment)
        case Success(None) => requester ! PaymentNotFound(paymentId)
        case Failure(error) => requester ! FindPaymentFailed(paymentId, error)
      }
    }

    private def currentBalance[C <: FiatCurrency](requester: ActorRef, currency: C): Unit = {
      client.currentBalances().onComplete {
        case Success(balances) =>
          self ! UpdatedBalances(balances)
          requester ! balances.find(_.currency == currency)
            .fold(balanceNotFound(currency))(BalanceRetrieved.apply)
        case Failure(error) =>
          requester ! BalanceRetrievalFailed(currency, error)
      }
    }

    private def pollBalances(): Unit = {
      client.currentBalances().map(UpdatedBalances.apply).pipeTo(self)
    }

    private def balanceNotFound(currency: FiatCurrency): RetrieveBalanceResponse =
      BalanceRetrievalFailed(currency, new NoSuchElementException("No balance in that currency"))

    private def updateBlockedFunds(balances: Seq[FiatAmount]): Unit = notifyingFundsAvailability {
      blockedFunds.updateBalances(balances)
    }

    private def unblockFunds(fundsId: FundsId): Unit = notifyingFundsAvailability {
      blockedFunds.unblock(fundsId)
    }

    private def notifyingFundsAvailability[T](block: => T): T = {
      val wereBacked = blockedFunds.areFundsBacked
      val result = block
      val areBacked = blockedFunds.areFundsBacked
      if (wereBacked != areBacked) {
        notifyFundsListeners(areBacked)
      }
      result
    }

    private def notifyFundsListeners(areBacked: Boolean): Unit = {
      val messageFactory = if (areBacked) AvailableFunds.apply _ else UnavailableFunds.apply _
      for ((funds, ref) <- blockedFunds.listenersByFundId) {
        ref ! messageFactory(funds)
      }
    }
  }
}

object OkPayProcessorActor {

  val Id = "OKPAY"

  private case object PollBalance

  private case class UpdatedBalances(balances: Seq[FiatAmount])

  def props(config: Config) = {
    val account = config.getString("coinffeine.okpay.id")
    val endpoint = URI.create(config.getString("coinffeine.okpay.endpoint"))
    val client = new OkPayWebServiceClient(
      account = config.getString("coinffeine.okpay.id"),
      seedToken = config.getString("coinffeine.okpay.token"),
      baseAddressOverride = Some(endpoint)
    )
    val pollingInterval =
      config.getDuration("coinffeine.okpay.pollingInterval", TimeUnit.MILLISECONDS).millis
    Props(new OkPayProcessorActor(account, client, pollingInterval))
  }
}
