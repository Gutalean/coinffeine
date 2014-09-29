package coinffeine.model.currency

private[currency] class IntegralCurrencyAmount[C <: Currency](currency: C)
  extends Integral[CurrencyAmount[C]] with Ordering[CurrencyAmount[C]] {

  override def plus(x: CurrencyAmount[C], y: CurrencyAmount[C]) = amount(x.value + y.value)
  override def times(x: CurrencyAmount[C], y: CurrencyAmount[C]) = amount(x.value * y.value)
  override def minus(x: CurrencyAmount[C], y: CurrencyAmount[C]) = amount(x.value - y.value)
  override def negate(x: CurrencyAmount[C]): CurrencyAmount[C] = amount(-x.value)
  override def quot(x: CurrencyAmount[C], y: CurrencyAmount[C]) = amount(x.value quot y.value)
  override def rem(x: CurrencyAmount[C], y: CurrencyAmount[C]) = amount(x.value % y.value)

  override def toDouble(x: CurrencyAmount[C]): Double = x.value.toDouble
  override def toFloat(x: CurrencyAmount[C]): Float = x.value.toFloat
  override def toLong(x: CurrencyAmount[C]): Long = x.value.toLong
  override def toInt(x: CurrencyAmount[C]): Int = x.value.toInt
  override def fromInt(x: Int): CurrencyAmount[C] = amount(BigDecimal(x))

  override def compare(x: CurrencyAmount[C], y: CurrencyAmount[C]): Int = x.value.compare(y.value)

  private def amount(value: BigDecimal) = CurrencyAmount(value, currency)
}

