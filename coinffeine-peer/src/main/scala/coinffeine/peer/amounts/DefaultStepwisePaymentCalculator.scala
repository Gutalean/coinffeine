package coinffeine.peer.amounts

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.payment.PaymentProcessor
import coinffeine.peer.amounts.StepwisePaymentCalculator.Payment

class DefaultStepwisePaymentCalculator(processor: PaymentProcessor)
  extends StepwisePaymentCalculator {

  override def maximumPaymentWithGrossAmount[C <: FiatCurrency](
      grossAmount: CurrencyAmount[C]): CurrencyAmount[C] = {
    require(grossAmount.isPositive, s"Cannot pay with a non-positive amount ($grossAmount given)")
    val step = processor.bestStepSize(grossAmount.currency)
    val stepWithFees = processor.amountPlusFee(step)
    val (completeSteps, grossRemainder) = grossAmount /% stepWithFees
    step * completeSteps + maximumRemainingPayment(grossRemainder)
  }

  override def maximumBreakableFiatAmount[C <: FiatCurrency](currency: C) =
    processor.bestStepSize(currency) * DefaultStepwisePaymentCalculator.MaxStepsPerExchange

  private def maximumRemainingPayment[C <: FiatCurrency](grossRemainder: CurrencyAmount[C]) =
    if (grossRemainder.isPositive) processor.amountMinusFee(grossRemainder)
    else CurrencyAmount.zero(grossRemainder.currency)

  override def breakIntoSteps[C <: FiatCurrency](netAmount: CurrencyAmount[C]): Seq[Payment[C]] = {
    require(netAmount.isPositive, s"Cannot pay with a non-positive amount ($netAmount given)")
    val step = processor.bestStepSize(netAmount.currency)
    val (completeSteps, netRemainder) = netAmount /% step
    val remainderStep = if (netRemainder.isPositive) Some(pay(netRemainder)) else None
    Seq.fill(completeSteps.toInt)(pay(step)) ++ remainderStep
  }

  private def pay[C <: FiatCurrency](netAmount: CurrencyAmount[C]) =
    Payment(netAmount, processor.calculateFee(netAmount))
}

object DefaultStepwisePaymentCalculator {
  private val MaxStepsPerExchange = 750
}
