package coinffeine.model.currency

case class FiatAmount(
    override val units: Long,
    override val currency: FiatCurrency) extends CurrencyAmount[FiatAmount] {

  override protected def fromUnits(units: Long) = FiatAmount(units, currency)

  override protected def fromExactValue(value: BigDecimal) = currency.exactAmount(value)
}

object FiatAmount {
  implicit object Numeric extends Ordering[FiatAmount] {

    override def compare(x: FiatAmount, y: FiatAmount) = {
      requireSameCurrency(x, y)
      x.units.compare(y.units)
    }

    private def requireSameCurrency(x: FiatAmount, y: FiatAmount): Unit = {
      require(x.currency == y.currency, s"Cannot mix ${x.currency} with ${y.currency}")
    }
  }
}
