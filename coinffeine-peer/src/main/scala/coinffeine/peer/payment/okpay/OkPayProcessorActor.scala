package coinffeine.peer.payment.okpay

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor._
import com.typesafe.config.Config

import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.payment.PaymentProcessor._
import coinffeine.peer.api.event.FiatBalanceChangeEvent
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.event.EventProducer
import coinffeine.peer.payment._

class OkPayProcessorActor(account: AccountId, client: OkPayClient, pollingInterval: FiniteDuration)
  extends Actor {

  import OkPayProcessorActor._
  import context.dispatcher

  override def receive: Receive = {
    case PaymentProcessorActor.Initialize(eventChannel) =>
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

    def start(): Unit = {
      context.become(managePayments)
    }

    val managePayments: Receive = {
      case PaymentProcessorActor.Identify =>
        sender ! PaymentProcessorActor.Identified(OkPayProcessorActor.Id)
      case pay: PaymentProcessorActor.Pay[_] =>
        sendPayment(sender(), pay)
      case PaymentProcessorActor.FindPayment(paymentId) =>
        findPayment(sender(), paymentId)
      case PaymentProcessorActor.RetrieveBalance(currency) =>
        currentBalance(sender(), currency)
      case PollBalance =>
        pollBalance()
    }

    private def sendPayment[C <: FiatCurrency](requester: ActorRef,
                                               pay: PaymentProcessorActor.Pay[C]): Unit = {
      client.sendPayment(pay.to, pay.amount, pay.comment).onComplete {
        case Success(payment) =>
          requester ! PaymentProcessorActor.Paid(payment)
        case Failure(error) =>
          requester ! PaymentProcessorActor.PaymentFailed(pay, error)
      }
    }

    private def findPayment(requester: ActorRef, paymentId: PaymentId): Unit = {
      client.findPayment(paymentId).onComplete {
        case Success(Some(payment)) => requester ! PaymentProcessorActor.PaymentFound(payment)
        case Success(None) => requester ! PaymentProcessorActor.PaymentNotFound(paymentId)
        case Failure(error) => requester ! PaymentProcessorActor.FindPaymentFailed(paymentId, error)
      }
    }

    private def currentBalance[C <: FiatCurrency](requester: ActorRef, currency: C): Unit = {
      client.currentBalance(currency).onComplete {
        case Success(balance) =>
          requester ! PaymentProcessorActor.BalanceRetrieved(balance)
          produceEvent(FiatBalanceChangeEvent(balance))
        case Failure(error) =>
          requester ! PaymentProcessorActor.BalanceRetrievalFailed(currency, error)
      }
    }

    private def pollBalance(): Unit = {
      client.currentBalance(CurrencyToPoll).onSuccess {
        case balance => produceEvent(FiatBalanceChangeEvent(balance))
      }
    }
  }
}

object OkPayProcessorActor {

  val Id = "OKPAY"

  private val CurrencyToPoll = Euro

  private case object PollBalance

  def props(config: Config) = {
    val account = config.getString("coinffeine.okpay.id")
    val client = new OkPayWebServiceClient(
      account = config.getString("coinffeine.okpay.id"),
      seedToken = config.getString("coinffeine.okpay.token")
    )
    val pollingInterval =
      config.getDuration("coinffeine.okpay.pollingInterval", TimeUnit.MILLISECONDS).millis
    Props(new OkPayProcessorActor(account, client, pollingInterval))
  }
}
