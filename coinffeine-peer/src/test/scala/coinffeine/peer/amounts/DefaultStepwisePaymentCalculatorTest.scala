package coinffeine.peer.amounts

import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.payment.okpay.OkPayPaymentProcessor
import coinffeine.peer.amounts.StepwisePaymentCalculator.Payment

class DefaultStepwisePaymentCalculatorTest extends UnitTest with PropertyChecks {

  val step = OkPayPaymentProcessor.bestStepSize(Euro)
  val stepFee = OkPayPaymentProcessor.calculateFee(step)
  val stepWithFees = step + stepFee
  val instance = new DefaultStepwisePaymentCalculator(OkPayPaymentProcessor)

  "The maximum payment with a gross amount" should "be computed for positive amounts" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      instance.maximumPaymentWithGrossAmount(0.EUR)
    }
  }

  it should "be zero for amounts lower or equal to the minimum fee" in {
    val minFee = OkPayPaymentProcessor.calculateFee(0.01.USD)
    instance.maximumPaymentWithGrossAmount(minFee) shouldBe 0.USD
  }

  it should "be lineal for multiples of the step-plus-fees amount" in {
    instance.maximumPaymentWithGrossAmount(stepWithFees * 7) shouldBe (step * 7)
  }

  it should "consider amounts that are not multiples of the step-plus-fees amount" in {
    val remainder = 1.5.EUR
    instance.maximumPaymentWithGrossAmount(stepWithFees * 2 + remainder) shouldBe
      step * 2 + OkPayPaymentProcessor.amountMinusFee(remainder)
  }

  "The step breakdown of a net amount" should "be computed for positive amounts" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      instance.breakIntoSteps(0.EUR)
    }
  }

  it should "have one step for amounts up to the step size" in {
    instance.breakIntoSteps(step / 2) shouldBe Seq(Payment(step / 2, stepFee))
    instance.breakIntoSteps(step) shouldBe Seq(Payment(step, stepFee))
  }

  it should "have identical steps for multiples of the step size" in {
    val breakdown = instance.breakIntoSteps(step * 6)
    breakdown.toSet shouldBe Set(Payment(step, stepFee))
    breakdown should have size 6
  }

  it should "have a different smaller final step for non-multiples of the step size" in {
    val breakdown = instance.breakIntoSteps(step * 6.5)
    breakdown.init.toSet shouldBe Set(Payment(step, stepFee))
    breakdown.init should have size 6
    breakdown.last shouldBe Payment(step / 2, stepFee)
  }

  "Maximum net payment and required gross amount" should "be consistent" in {
    forAll (Gen.posNum[Int]) { cents =>
      val grossAmount = Euro.fromUnits(cents)
      val maxNetPayment = instance.maximumPaymentWithGrossAmount(grossAmount)
      if (maxNetPayment.isPositive) {
        val requiredGrossAmount = instance.requiredAmountToPay(maxNetPayment)
        requiredGrossAmount.value should be <= grossAmount.value
      }
    }
  }
}
