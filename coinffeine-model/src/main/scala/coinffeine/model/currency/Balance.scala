package coinffeine.model.currency

import coinffeine.model.currency.Currency.Bitcoin

trait Balance[C <: Currency] {
  def amount: CurrencyAmount[C]
  def hasExpired: Boolean
}

case class BitcoinBalance(
  amount: BitcoinAmount,
  minOutput: Option[BitcoinAmount],
  blocked: BitcoinAmount = Bitcoin.Zero,
  hasExpired: Boolean = false) extends Balance[Bitcoin.type] {

  def plus(what: BitcoinAmount) = copy(amount = amount + what, minOutput = plusOutput(what))

  private def plusOutput(output: BitcoinAmount): Some[BitcoinAmount] = minOutput match {
    case Some(prev) => Some(prev.min(output))
    case None => Some(output)
  }
}

object BitcoinBalance {

  def singleOutput(amount: BitcoinAmount) = BitcoinBalance(
    amount,
    minOutput = Some(amount))
}

case class FiatBalance[C <: FiatCurrency](
  amount: CurrencyAmount[C],
  hasExpired: Boolean = false) extends Balance[C]

