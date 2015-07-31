package coinffeine.model.currency.balance

import coinffeine.model.currency._

case class BitcoinBalance(
    estimated: BitcoinAmount,
    available: BitcoinAmount,
    minOutput: Option[BitcoinAmount],
    blocked: BitcoinAmount = Bitcoin.zero) {

  def amount = estimated
}

object BitcoinBalance {

  val empty = BitcoinBalance(
    estimated = Bitcoin.zero,
    available = Bitcoin.zero,
    minOutput = None)

  def singleOutput(amount: BitcoinAmount) = BitcoinBalance(
    estimated = amount,
    available = amount,
    minOutput = Some(amount))
}

case class FiatBalance(
    amounts: FiatAmounts,
    blockedAmounts: FiatAmounts,
    remainingLimits: FiatAmounts)

object FiatBalance {
  val empty = FiatBalance(FiatAmounts.empty, FiatAmounts.empty, FiatAmounts.empty)
}
