package coinffeine.model.currency

import coinffeine.model.currency.Currency.Bitcoin

trait Balance[C <: Currency] {
  def amount: CurrencyAmount[C]
  def hasExpired: Boolean
}

case class BitcoinBalance(
  amount: BitcoinAmount,
  blocked: BitcoinAmount = Bitcoin.Zero,
  hasExpired: Boolean = false) extends Balance[Bitcoin.type]

case class FiatBalance[C <: FiatCurrency](
  amount: CurrencyAmount[C],
  hasExpired: Boolean = false) extends Balance[C]

