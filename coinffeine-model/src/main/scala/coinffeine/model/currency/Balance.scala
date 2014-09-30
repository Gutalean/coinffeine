package coinffeine.model.currency

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

