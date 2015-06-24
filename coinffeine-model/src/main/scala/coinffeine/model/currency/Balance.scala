package coinffeine.model.currency

trait Balance[C <: Currency] {
  def amount: CurrencyAmount[C]
  def hasExpired: Boolean
}

case class BitcoinBalance(
  estimated: Bitcoin.Amount,
  available: Bitcoin.Amount,
  minOutput: Option[Bitcoin.Amount],
  blocked: Bitcoin.Amount = Bitcoin.zero,
  hasExpired: Boolean = false) extends Balance[Bitcoin.type] {

  val amount = estimated
}

object BitcoinBalance {

  def singleOutput(amount: Bitcoin.Amount) = BitcoinBalance(
    estimated = amount,
    available = amount,
    minOutput = Some(amount))
}

case class FiatBalance[C <: FiatCurrency](
  amount: CurrencyAmount[C],
  hasExpired: Boolean = false) extends Balance[C]

