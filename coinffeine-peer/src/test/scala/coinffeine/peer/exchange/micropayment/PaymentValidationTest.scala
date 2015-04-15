package coinffeine.peer.exchange.micropayment

import scalaz.Failure
import scalaz.syntax.validation._

import org.scalatest.Inside

import coinffeine.common.test.UnitTest
import coinffeine.model.Both
import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange.StepBreakdown
import coinffeine.model.exchange.{Exchange, ExchangeId}
import coinffeine.model.payment.Payment
import coinffeine.peer.exchange.micropayment.PaymentValidation._
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.{FinalStep, IntermediateStep}

class PaymentValidationTest extends UnitTest with Inside {

  private val exchangeId = ExchangeId.random()
  private val breakdown = StepBreakdown(2)
  private val participants = Both(
    buyer = "account1",
    seller = "account2"
  )
  private val payment1 = Payment(
    id = "payment1",
    senderId = participants.buyer,
    receiverId = participants.seller,
    amount = 2.EUR,
    date = null,
    description = s"Payment for exchange ${exchangeId.value}, step 1",
    completed = true
  )
  private val payment2 = payment1.copy(
    id = "payment2",
    amount = 1.EUR,
    description = s"Payment for exchange ${exchangeId.value}, step 2"
  )
  private val amounts = Seq(
    Exchange.IntermediateStepAmounts(
      depositSplit = Both(0.BTC, 3.BTC),
      fiatAmount = 2.EUR,
      fiatFee = 0.2.EUR,
      progress = null
    ),
    Exchange.IntermediateStepAmounts(
      depositSplit = Both(2.BTC, 1.BTC),
      fiatAmount = 1.EUR,
      fiatFee = 0.1.EUR,
      progress = null
    )
  )
  private val firstStep = IntermediateStep(1, breakdown)
  private val secondStep = IntermediateStep(2, breakdown)

  private val validate = new PaymentValidation(exchangeId, amounts, participants)

  "Payment validation" should "reject any payment at the last step" in {
    validate(FinalStep(breakdown), payment1) shouldBe UnexpectedPayment.failureNel
  }

  it should "require the amount corresponding with the step" in {
    validate(firstStep, payment1.copy(amount = 0.3.EUR)) shouldBe
      InvalidAmount(actual = 0.3.EUR, expected = 2.EUR).failureNel
    validate(secondStep, payment2.copy(amount = 2.EUR)) shouldBe
      InvalidAmount(actual = 2.EUR, expected = 1.EUR).failureNel
  }

  it should "require having the participants account ids" in {
    validate(firstStep, payment1.copy(senderId = "other")) shouldBe 'failure
    validate(firstStep, payment1.copy(receiverId = "other")) shouldBe 'failure
    validate(firstStep, payment1.copy(senderId = "wrong", receiverId = "ids")) shouldBe
      InvalidAccounts(actual = Both("wrong", "ids"), expected = participants).failureNel
  }

  it should "require have a valid description" in {
    validate(firstStep, payment1.copy(description = "invalid")) shouldBe
      InvalidDescription(actual = "invalid", expected = payment1.description).failureNel
  }

  it should "require the payment to be completed" in {
    validate(firstStep, payment1.copy(completed = false)) shouldBe IncompletePayment.failureNel
  }

  it should "accept a valid payment" in {
    validate(firstStep, payment1) shouldBe 'success
    validate(secondStep, payment2) shouldBe 'success
  }

  it should "accumulate multiple errors" in {
    val veryWrongPayment = payment1.copy(
      amount = 0.1.EUR,
      senderId = "other sender",
      receiverId = "not me",
      description = "invalid description",
      completed = false
    )
    inside(validate(firstStep, veryWrongPayment)) {
      case Failure(errors) => errors should have size 4
    }
  }
}
