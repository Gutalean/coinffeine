package coinffeine.peer.exchange.micropayment

import scalaz.Scalaz._
import scalaz.ValidationNel

import coinffeine.model.Both
import coinffeine.model.currency.FiatAmount
import coinffeine.model.exchange.ActiveExchange._
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.payment.Payment
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.{FinalStep, IntermediateStep, Step}

private class PaymentValidation(
    exchangeId: ExchangeId,
    amounts: Seq[IntermediateStepAmounts],
    participants: Both[AccountId]) {
  import PaymentValidation._

  def apply(step: Step, payment: Payment): Result = step match {
    case _: FinalStep => UnexpectedPayment.failureNel
    case intermediateStep: IntermediateStep =>
      validateIntermediateStep(intermediateStep, payment)
  }

  private def validateIntermediateStep(step: IntermediateStep, payment: Payment): Result = {

    val amountValidation: Result = {
      val expectedAmount = step.select(amounts).fiatAmount
      if (expectedAmount == payment.amount) ().successNel
      else InvalidAmount(actual = payment.amount, expected = expectedAmount).failureNel
    }

    val accountsValidation: Result = {
      val actualParticipants = Both(buyer = payment.senderId, seller = payment.receiverId)
      if (actualParticipants == participants) ().successNel
      else InvalidAccounts(actualParticipants, participants).failureNel
    }

    val invoiceValidation: Result = {
      val expectedInvoice = PaymentFields.invoice(exchangeId, step)
      if (payment.invoice == expectedInvoice) ().successNel
      else InvalidInvoice(payment.invoice, expectedInvoice).failureNel
    }

    val completenessValidation: Result =
      if (payment.completed) ().successNel else IncompletePayment.failureNel

    amountValidation *> accountsValidation *> invoiceValidation *> completenessValidation
  }
}

private object PaymentValidation {
  type Result = ValidationNel[Error, Unit]

  sealed trait Error {
    def message: String
  }

  case object UnexpectedPayment extends Error {
    override def message = "No payment was expected at the last step"
  }

  case class InvalidAmount(actual: FiatAmount, expected: FiatAmount) extends Error {
    override def message = s"Expected a payment of $expected but $actual was payed"
  }

  case class InvalidAccounts(actual: Both[AccountId], expected: Both[AccountId]) extends Error {
    override def message =
      s"Expected a payment from ${expected.buyer} to ${expected.seller}" +
      s" but was from ${actual.buyer} to ${actual.seller}"
  }

  case class InvalidInvoice(actual: String, expected: String) extends Error {
    override def message = s"Invalid invoice '$actual' where '$expected' was expected"
  }

  case object IncompletePayment extends Error {
    override def message = "Payment is not yet complete"
  }
}
