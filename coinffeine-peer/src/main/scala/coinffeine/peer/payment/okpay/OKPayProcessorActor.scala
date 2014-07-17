package coinffeine.peer.payment.okpay

import scala.util.{Failure, Success}

import akka.actor.{Actor, ActorRef, Props}

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.payment.PaymentProcessor._
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.payment._
import coinffeine.peer.payment.okpay.generated._

class OKPayProcessorActor(
    account: String,
    client: OkPayClient) extends Actor {

  override def receive: Receive = {
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
      case Success(balance) => requester ! PaymentProcessor.BalanceRetrieved(balance)
      case Failure(error) => requester ! PaymentProcessor.BalanceRetrievalFailed(currency, error)
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
