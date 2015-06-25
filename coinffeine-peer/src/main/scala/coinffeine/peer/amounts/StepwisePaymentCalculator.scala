package coinffeine.peer.amounts

import coinffeine.model.currency.{FiatAmount, FiatCurrency}

/** Calculates amounts related with stepwise fiat payments. */
trait StepwisePaymentCalculator {

  /** Computes the maximum net amount that can be payed with a given gross amount in a series of
    * steps. Note that the minimum fee can make small amounts able to pay no net amount at all.
    */
  def maximumPaymentWithGrossAmount(
      grossAmount: FiatAmount): FiatAmount

  /** Computes the maximum fiat amount that can be break into steps. */
  def maximumBreakableFiatAmount(currency: FiatCurrency): FiatAmount

  /** Break into steps with their corresponding fees a net amount to be payed. */
  def breakIntoSteps(
      netAmount: FiatAmount): Seq[StepwisePaymentCalculator.Payment]

  /** Gross amount required to pay in stepwise fashion a given net amount. */
  def requiredAmountToPay(netAmount: FiatAmount): FiatAmount =
    breakIntoSteps(netAmount).map(_.grossAmount).reduce(_ + _)
}

object StepwisePaymentCalculator {

  case class Payment(netAmount: FiatAmount, fee: FiatAmount) {
    def grossAmount: FiatAmount = netAmount + fee
  }

}
