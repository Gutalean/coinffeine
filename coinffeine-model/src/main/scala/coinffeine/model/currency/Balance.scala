package coinffeine.model.currency

import coinffeine.model.currency.Currency.Bitcoin

trait Balance[C <: Currency] {
  def amount: CurrencyAmount[C]
  def hasExpired: Boolean
}

case class BitcoinBalance(
  estimated: BitcoinAmount,
  available: BitcoinAmount,
  minOutput: Option[BitcoinAmount],
  blocked: BitcoinAmount = Bitcoin.Zero,
  hasExpired: Boolean = false) extends Balance[Bitcoin.type] {

  val amount = estimated

  def plus(amount: BitcoinAmount) = copy(
    estimated = estimated + amount,
    available = estimated + amount,
    minOutput = plusOutput(amount))

  private def plusOutput(output: BitcoinAmount): Some[BitcoinAmount] = minOutput match {
    case Some(prev) => Some(prev.min(output))
    case None => Some(output)
  }
}

object BitcoinBalance {

  def singleOutput(amount: BitcoinAmount) = BitcoinBalance(
    estimated = amount,
    available = amount,
    minOutput = Some(amount))
}

case class FiatBalance[C <: FiatCurrency](
  amount: CurrencyAmount[C],
  hasExpired: Boolean = false) extends Balance[C]

