package coinffeine.model.currency.balance

import coinffeine.model.currency._

trait Balance[A <: CurrencyAmount[A]] {
  def amount: A
}

case class BitcoinBalance(
    estimated: BitcoinAmount,
    available: BitcoinAmount,
    minOutput: Option[BitcoinAmount],
    blocked: BitcoinAmount = Bitcoin.zero) extends Balance[BitcoinAmount] {

  override val amount = estimated
}

object BitcoinBalance {

  def singleOutput(amount: BitcoinAmount) = BitcoinBalance(
    estimated = amount,
    available = amount,
    minOutput = Some(amount))
}

case class FiatBalance(amounts: FiatAmounts) extends Balance[FiatAmount] {
  override def amount = amounts(Euro)
}

object FiatBalance {
  val empty = FiatBalance(FiatAmounts.empty)
}
