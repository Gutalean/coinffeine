package coinffeine.peer.amounts

import coinffeine.model.currency.{FiatAmount, FiatCurrency}
import coinffeine.model.payment.PaymentProcessor
import coinffeine.peer.amounts.StepwisePaymentCalculator.Payment

class DefaultStepwisePaymentCalculator(processor: PaymentProcessor)
  extends StepwisePaymentCalculator {

  override def maximumPaymentWithGrossAmount(grossAmount: FiatAmount): FiatAmount = {
    require(grossAmount.isPositive, s"Cannot pay with a non-positive amount ($grossAmount given)")
    val step = processor.bestStepSize(grossAmount.currency)
    val stepWithFees = processor.amountPlusFee(step)
    val (completeSteps, grossRemainder) = grossAmount /% stepWithFees
    step * completeSteps + maximumRemainingPayment(grossRemainder)
  }

  override def maximumBreakableFiatAmount(currency: FiatCurrency) =
    processor.bestStepSize(currency) * DefaultStepwisePaymentCalculator.MaxStepsPerExchange

  private def maximumRemainingPayment(grossRemainder: FiatAmount) =
    if (grossRemainder.isPositive) processor.amountMinusFee(grossRemainder)
    else grossRemainder.currency.zero

  override def breakIntoSteps(netAmount: FiatAmount): Seq[Payment] = {
    require(netAmount.isPositive, s"Cannot pay with a non-positive amount ($netAmount given)")
    val step = processor.bestStepSize(netAmount.currency)
    val (completeSteps, netRemainder) = netAmount /% step
    val remainderStep = if (netRemainder.isPositive) Some(pay(netRemainder)) else None
    Seq.fill(completeSteps.toInt)(pay(step)) ++ remainderStep
  }

  private def pay(netAmount: FiatAmount) =
    Payment(netAmount, processor.calculateFee(netAmount))
}

object DefaultStepwisePaymentCalculator {
  private val MaxStepsPerExchange = 750
}
