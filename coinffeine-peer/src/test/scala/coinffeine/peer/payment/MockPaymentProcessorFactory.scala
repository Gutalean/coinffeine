package coinffeine.peer.payment

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props}
import org.joda.time.DateTime

import coinffeine.model.currency.{CurrencyAmount, FiatAmount, FiatCurrency}
import coinffeine.model.payment.Payment

class MockPaymentProcessorFactory(initialPayments: List[AnyPayment] = List.empty) {

  @volatile var payments: List[AnyPayment] = initialPayments

  private class MockPaymentProcessor(
      fiatAddress: String,
      initialBalances: Seq[FiatAmount]) extends Actor {

    val id: String = "MockPay"

    override def receive: Receive = {
      case PaymentProcessorActor.Identify =>
        sender ! PaymentProcessorActor.Identified(id)
      case pay: PaymentProcessorActor.Pay[_] =>
        sendPayment(sender(), pay)
      case PaymentProcessorActor.FindPayment(paymentId) =>
        findPayment(sender(), paymentId)
      case PaymentProcessorActor.RetrieveBalance(currency) =>
        currentBalance(sender(), currency)
    }


    private def findPayment(requester: ActorRef, paymentId: String): Unit =
      payments.find(_.id == paymentId) match {
        case Some(payment) => requester ! PaymentProcessorActor.PaymentFound(payment)
        case None => requester ! PaymentProcessorActor.PaymentNotFound(paymentId)
      }

    private def currentBalance[C <: FiatCurrency](requester: ActorRef, currency: C): Unit = {
      val deltas: List[CurrencyAmount[C]] = payments.collect {
        case Payment(_, `fiatAddress`, `fiatAddress`, out: CurrencyAmount[C], _, _, _) => currency.Zero
        case Payment(_, _, `fiatAddress`, in: CurrencyAmount[C], _, _, _) => in
        case Payment(_, `fiatAddress`, _, out: CurrencyAmount[C], _, _, _) => -out
      }
      val initial = initialBalances.collectFirst {
        case a: CurrencyAmount[C] => a
      }.getOrElse(currency.Zero)
      val balance = deltas.foldLeft(initial)(_ + _)
      requester ! PaymentProcessorActor.BalanceRetrieved(balance)
    }

    private def sendPayment[C <: FiatCurrency](
        requester: ActorRef, pay: PaymentProcessorActor.Pay[C]): Unit =
      if (initialBalances.map(_.currency).contains(pay.amount.currency)) {
        val payment = Payment(
          UUID.randomUUID().toString,
          fiatAddress,
          pay.to,
          pay.amount,
          DateTime.now(),
          pay.comment,
          completed = true)
        payments = payment :: payments
        requester ! PaymentProcessorActor.Paid(payment)
      } else {
        requester ! PaymentProcessorActor.PaymentFailed(
          pay, new Error("[MockPay] The user does not have an account with that currency."))
      }
  }

  def newProcessor(
      fiatAddress: String, initialBalance: Seq[FiatAmount] = Seq.empty): Props =
    Props(new MockPaymentProcessor(fiatAddress, initialBalance))
}
