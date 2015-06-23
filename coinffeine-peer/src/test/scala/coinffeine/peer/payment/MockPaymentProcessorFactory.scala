package coinffeine.peer.payment

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props}
import org.joda.time.DateTime

import coinffeine.model.currency.{CurrencyAmount, FiatAmount, FiatCurrency}
import coinffeine.model.payment.{AnyPayment, Payment}
import coinffeine.peer.payment.PaymentProcessorActor.FindPaymentCriterion

class MockPaymentProcessorFactory(initialPayments: List[AnyPayment] = List.empty) {

  @volatile var payments: List[AnyPayment] = initialPayments

  private class MockPaymentProcessor(
      fiatAddress: String,
      initialBalances: Seq[FiatAmount]) extends Actor {

    override def receive: Receive = {
      case pay: PaymentProcessorActor.Pay[_] =>
        sendPayment(sender(), pay)
      case PaymentProcessorActor.FindPayment(criterion) =>
        findPayment(sender(), criterion)
      case PaymentProcessorActor.RetrieveBalance(currency) =>
        currentBalance(sender(), currency)
    }

    private def findPayment(requester: ActorRef, criterion: FindPaymentCriterion): Unit = {
      def predicate(payment: AnyPayment) = criterion match {
        case FindPaymentCriterion.ById(paymentId) => payment.id == paymentId
        case FindPaymentCriterion.ByInvoice(invoice) => payment.invoice == invoice
      }
      payments.find(predicate) match {
        case Some(payment) => requester ! PaymentProcessorActor.PaymentFound(payment)
        case None => requester ! PaymentProcessorActor.PaymentNotFound(criterion)
      }
    }

    private def currentBalance[C <: FiatCurrency](requester: ActorRef, currency: C): Unit = {
      val deltas: List[CurrencyAmount[C]] = paymentsForCurrency(currency).collect {
        case Payment(_, `fiatAddress`, `fiatAddress`, out, _, _, _, _) => CurrencyAmount.zero(currency)
        case Payment(_, _, `fiatAddress`, in, _, _, _, _) => in
        case Payment(_, `fiatAddress`, _, out, _, _, _, _) => -out
      }
      val initial = initialBalances.collectFirst {
        case a if a.currency == currency => a.asInstanceOf[CurrencyAmount[C]]
      }.getOrElse(CurrencyAmount.zero(currency))
      implicit val num = initial.numeric
      val balance = initial + deltas.sum
      requester ! PaymentProcessorActor.BalanceRetrieved(balance, num.fromInt(0))
    }

    private def paymentsForCurrency[C <: FiatCurrency](currency: C): List[Payment[C]] =
      payments.filter(_.amount.currency == currency).asInstanceOf[List[Payment[C]]]

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
          pay.invoice,
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
