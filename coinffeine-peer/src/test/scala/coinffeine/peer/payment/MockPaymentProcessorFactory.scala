package coinffeine.peer.payment

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props}
import org.joda.time.DateTime

import coinffeine.model.currency.{FiatAmounts, FiatAmount, FiatCurrency}
import coinffeine.model.payment.Payment
import coinffeine.peer.payment.PaymentProcessorActor.FindPaymentCriterion

class MockPaymentProcessorFactory(initialPayments: List[Payment] = List.empty) {

  @volatile var payments: List[Payment] = initialPayments

  private class MockPaymentProcessor(
      fiatAddress: String, initialBalances: FiatAmounts) extends Actor {

    override def receive: Receive = {
      case pay: PaymentProcessorActor.Pay =>
        sendPayment(sender(), pay)
      case PaymentProcessorActor.FindPayment(criterion) =>
        findPayment(sender(), criterion)
      case PaymentProcessorActor.RetrieveBalance(currency) =>
        currentBalance(sender(), currency)
    }

    private def findPayment(requester: ActorRef, criterion: FindPaymentCriterion): Unit = {
      def predicate(payment: Payment) = criterion match {
        case FindPaymentCriterion.ById(paymentId) => payment.id == paymentId
        case FindPaymentCriterion.ByInvoice(invoice) => payment.invoice == invoice
      }
      payments.find(predicate) match {
        case Some(payment) => requester ! PaymentProcessorActor.PaymentFound(payment)
        case None => requester ! PaymentProcessorActor.PaymentNotFound(criterion)
      }
    }

    private def currentBalance(requester: ActorRef, currency: FiatCurrency): Unit = {
      val deltas: List[FiatAmount] = paymentsForCurrency(currency).collect {
        case Payment(_, `fiatAddress`, `fiatAddress`, out, _, _, _, _, _) => currency.zero
        case Payment(_, _, `fiatAddress`, in, _, _, _, _, _) => in
        case Payment(_, `fiatAddress`, _, out, _, _, _, _, _) => -out
      }
      val initial = initialBalances.get(currency).getOrElse(currency.zero)
      val balance = initial + currency.sum(deltas)
      requester ! PaymentProcessorActor.BalanceRetrieved(balance, currency.zero)
    }

    private def paymentsForCurrency(currency: FiatCurrency): List[Payment] =
      payments.filter(_.amount.currency == currency)

    private def sendPayment(requester: ActorRef, pay: PaymentProcessorActor.Pay): Unit =
      if (initialBalances.contains(pay.amount.currency)) {
        val payment = Payment(
          id = UUID.randomUUID().toString,
          senderId = fiatAddress,
          receiverId = pay.to,
          amount = pay.amount,
          fee = pay.amount.currency.zero,
          date = DateTime.now(),
          description = pay.comment,
          invoice = pay.invoice,
          completed = true)
        payments = payment :: payments
        requester ! PaymentProcessorActor.Paid(payment)
      } else {
        requester ! PaymentProcessorActor.PaymentFailed(
          pay, new Error("[MockPay] The user does not have an account with that currency."))
      }
  }

  def newProcessor(
      fiatAddress: String, initialBalance: FiatAmounts = FiatAmounts.empty): Props =
    Props(new MockPaymentProcessor(fiatAddress, initialBalance))
}
