package coinffeine.model.currency.balance

import coinffeine.model.currency._

trait Balance[A <: CurrencyAmount[A]] {
  def amount: A

  def hasExpired: Boolean
}

case class BitcoinBalance(
    estimated: BitcoinAmount,
    available: BitcoinAmount,
    minOutput: Option[BitcoinAmount],
    blocked: BitcoinAmount = Bitcoin.zero,
    hasExpired: Boolean = false) extends Balance[BitcoinAmount] {

  val amount = estimated
}

object BitcoinBalance {

  def singleOutput(amount: BitcoinAmount) = BitcoinBalance(
    estimated = amount,
    available = amount,
    minOutput = Some(amount))
}

case class FiatBalance(
    amount: FiatAmount,
    hasExpired: Boolean = false) extends Balance[FiatAmount]

