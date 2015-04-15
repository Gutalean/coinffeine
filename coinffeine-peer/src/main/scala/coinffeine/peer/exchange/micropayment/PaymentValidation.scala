package coinffeine.peer.exchange.micropayment

import scalaz.ValidationNel
import scalaz.Scalaz._

import coinffeine.model.Both
import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange.{Exchange, ExchangeId}
import coinffeine.model.payment.Payment
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.{IntermediateStep, FinalStep, Step}

private class PaymentValidation[C <: FiatCurrency](
    exchangeId: ExchangeId,
    amounts: Seq[Exchange.IntermediateStepAmounts[C]],
    participants: Both[AccountId]) {
  import PaymentValidation._

  def apply(step: Step, payment: Payment[C]): Result = step match {
    case _: FinalStep => UnexpectedPayment.failureNel
    case intermediateStep: IntermediateStep => validateIntermediateStep(intermediateStep, payment)
  }

  private def validateIntermediateStep(step: IntermediateStep, payment: Payment[C]): Result = {

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

    val descriptionValidation: Result = {
      val expectedDescription = PaymentDescription(exchangeId, step)
      if (payment.description == expectedDescription) ().successNel
      else InvalidDescription(payment.description, expectedDescription).failureNel
    }

    val completenessValidation: Result =
      if (payment.completed) ().successNel else IncompletePayment.failureNel

    (amountValidation |@| accountsValidation |@| descriptionValidation |@| completenessValidation) {
      _ |+| _ |+| _ |+| _
    }
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

  case class InvalidAmount(actual: CurrencyAmount[_ <: FiatCurrency],
                           expected: CurrencyAmount[_ <: FiatCurrency]) extends Error {
    override def message = s"Expected a payment of $expected but $actual was payed"
  }

  case class InvalidAccounts(actual: Both[AccountId], expected: Both[AccountId]) extends Error {
    override def message =
      s"Expected a payment from ${expected.buyer} to ${expected.seller}" +
      s" but was from ${actual.buyer} to ${actual.seller}"
  }

  case class InvalidDescription(actual: String, expected: String) extends Error {
    override def message = s"Invalid description '$actual' where '$expected' was expected"
  }

  case object IncompletePayment extends Error {
    override def message = "Payment is not yet complete"
  }
}
