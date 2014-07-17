package coinffeine.peer.payment.okpay

import scala.util.{Failure, Success}

import akka.actor.{Actor, ActorRef, Props}

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.payment.PaymentProcessor._
import coinffeine.peer.api.event.FiatBalanceChangeEvent
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.event.EventProducer
import coinffeine.peer.payment._

class OKPayProcessorActor(account: AccountId, client: OkPayClient) extends Actor {

  override def receive: Receive = {
    case PaymentProcessor.Initialize(eventChannel) =>
      new InitializedBehavior(eventChannel).start()
  }

  private class InitializedBehavior(eventChannel: ActorRef) extends EventProducer(eventChannel) {
    import context.dispatcher

    def start(): Unit = {
      context.become(managePayments)
    }

    val managePayments: Receive = {
      case PaymentProcessor.Identify =>
        sender ! PaymentProcessor.Identified(OKPayProcessorActor.Id)
      case pay: PaymentProcessor.Pay[_] =>
        sendPayment(sender(), pay)
      case PaymentProcessor.FindPayment(paymentId) =>
        findPayment(sender(), paymentId)
      case PaymentProcessor.RetrieveBalance(currency) =>
        currentBalance(sender(), currency)
    }

    private def sendPayment[C <: FiatCurrency](requester: ActorRef,
                                               pay: PaymentProcessor.Pay[C]): Unit = {
      client.sendPayment(pay.to, pay.amount, pay.comment).onComplete {
        case Success(payment) =>
          requester ! PaymentProcessor.Paid(payment)
        case Failure(error) =>
          requester ! PaymentProcessor.PaymentFailed(pay, error)
      }
    }

    private def findPayment(requester: ActorRef, paymentId: PaymentId): Unit = {
      client.findPayment(paymentId).onComplete {
        case Success(Some(payment)) => requester ! PaymentProcessor.PaymentFound(payment)
        case Success(None) => requester ! PaymentProcessor.PaymentNotFound(paymentId)
        case Failure(error) => requester ! PaymentProcessor.FindPaymentFailed(paymentId, error)
      }
    }

    private def currentBalance[C <: FiatCurrency](requester: ActorRef, currency: C): Unit = {
      client.currentBalance(currency).onComplete {
        case Success(balance) =>
          requester ! PaymentProcessor.BalanceRetrieved(balance)
          produceEvent(FiatBalanceChangeEvent(balance))
        case Failure(error) =>
          requester ! PaymentProcessor.BalanceRetrievalFailed(currency, error)
      }
    }
  }
}

object OKPayProcessorActor {

  val Id = "OKPAY"

  trait Component extends PaymentProcessor.Component { this: ConfigComponent =>
    override lazy val paymentProcessorProps = Props {
      val accountId = config.getString("coinffeine.okpay.id")
      val tokenGenerator = new TokenGenerator(config.getString("coinffeine.okpay.token"))
      new OKPayProcessorActor(accountId, new OkPayWebServiceClient(accountId, tokenGenerator))
    }
  }
}
