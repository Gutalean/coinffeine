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

  def singleOutput(amount: BitcoinAmount) = BitcoinBalance(
    estimated = amount,
    available = amount,
    minOutput = Some(amount))
}

case class FiatBalance(amounts: FiatAmounts)

object FiatBalance {
  val empty = FiatBalance(FiatAmounts.empty)
}
