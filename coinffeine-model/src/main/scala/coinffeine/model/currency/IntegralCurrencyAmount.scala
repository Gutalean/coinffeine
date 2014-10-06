package coinffeine.model.currency

private[currency] class IntegralCurrencyAmount[C <: Currency](currency: C)
  extends Integral[CurrencyAmount[C]] with Ordering[CurrencyAmount[C]] {

  override def plus(x: CurrencyAmount[C], y: CurrencyAmount[C]) = amount(x.units + y.units)
  override def times(x: CurrencyAmount[C], y: CurrencyAmount[C]) = amount(x.units * y.units)
  override def minus(x: CurrencyAmount[C], y: CurrencyAmount[C]) = amount(x.units - y.units)
  override def negate(x: CurrencyAmount[C]): CurrencyAmount[C] = amount(-x.units)
  override def quot(x: CurrencyAmount[C], y: CurrencyAmount[C]) = amount(x.units / y.units)
  override def rem(x: CurrencyAmount[C], y: CurrencyAmount[C]) = amount(x.units % y.units)

  override def toDouble(x: CurrencyAmount[C]): Double = x.units.toDouble
  override def toFloat(x: CurrencyAmount[C]): Float = x.units.toFloat
  override def toLong(x: CurrencyAmount[C]): Long = x.units
  override def toInt(x: CurrencyAmount[C]): Int = x.units.toInt
  override def fromInt(x: Int): CurrencyAmount[C] = amount(x)

  override def compare(x: CurrencyAmount[C], y: CurrencyAmount[C]): Int = x.units.compare(y.units)

  private def amount(units: Long) = CurrencyAmount(units, currency)
}

