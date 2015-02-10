package coinffeine.peer.amounts

import coinffeine.model.currency.{FiatCurrency, CurrencyAmount}

/** Calculates amounts related with stepwise fiat payments. */
trait StepwisePaymentCalculator {

  /** Computes the maximum net amount that can be payed with a given gross amount in a series of
    * steps. Note that the minimum fee can make small amounts able to pay no net amount at all.
    */
  def maximumPaymentWithGrossAmount[C <: FiatCurrency](
      grossAmount: CurrencyAmount[C]): CurrencyAmount[C]

  /** Computes the maximum fiat amount that can be break into steps. */
  def maximumBreakableFiatAmount[C <: FiatCurrency](currency: C): CurrencyAmount[C]

  /** Break into steps with their corresponding fees a net amount to be payed. */
  def breakIntoSteps[C <: FiatCurrency](
      netAmount: CurrencyAmount[C]): Seq[StepwisePaymentCalculator.Payment[C]]

  /** Gross amount required to pay in stepwise fashion a given net amount. */
  def requiredAmountToPay[C <: FiatCurrency](netAmount: CurrencyAmount[C]): CurrencyAmount[C] =
    breakIntoSteps(netAmount).map(_.grossAmount).reduce(_ + _)
}

object StepwisePaymentCalculator {
  case class Payment[C <: FiatCurrency](netAmount: CurrencyAmount[C], fee: CurrencyAmount[C]) {
    def grossAmount: CurrencyAmount[C] = netAmount + fee
  }
}
