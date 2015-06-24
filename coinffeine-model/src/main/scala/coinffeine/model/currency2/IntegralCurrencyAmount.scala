package coinffeine.model.currency2

private trait IntegralCurrencyAmount[A <: CurrencyAmount[A]]
  extends Integral[A] with Ordering[A] {

  override def plus(x: A, y: A) = x + y
  override def times(x: A, y: A) = amount(x.units * y.units)
  override def minus(x: A, y: A) = x - y
  override def negate(x: A): A = -x
  override def quot(x: A, y: A) = amount(x.units / y.units)
  override def rem(x: A, y: A) = amount(x.units % y.units)

  override def toDouble(x: A): Double = x.units.toDouble
  override def toFloat(x: A): Float = x.units.toFloat
  override def toLong(x: A): Long = x.units
  override def toInt(x: A): Int = x.units.toInt
  override def fromInt(x: Int): A = amount(x)

  override def compare(x: A, y: A): Int = x.units.compare(y.units)

  protected def amount(units: Long): A
}

