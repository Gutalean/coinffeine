package coinffeine.model.currency

case class BitcoinAmount(units: Long) extends CurrencyAmount[BitcoinAmount] {

  override lazy val currency = Bitcoin

  override protected def fromUnits(units: Long) = BitcoinAmount(units)

  override protected def fromExactValue(value: BigDecimal) = currency.exactAmount(value)
}

object BitcoinAmount {
  implicit lazy val numeric: Integral[BitcoinAmount] with Ordering[BitcoinAmount] =
    new IntegralCurrencyAmount[BitcoinAmount] {
      override protected def amount(units: Long) = BitcoinAmount(units)
    }
}
