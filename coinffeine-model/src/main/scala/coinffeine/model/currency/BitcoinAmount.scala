package coinffeine.model.currency

case class BitcoinAmount(units: Long) extends CurrencyAmount[BitcoinAmount] {

  override lazy val currency = Bitcoin

  override protected def fromUnits(units: Long) = BitcoinAmount(units)

  override protected def fromExactValue(value: BigDecimal) = currency.exactAmount(value)
}

object BitcoinAmount {

  implicit object Numeric extends Integral[BitcoinAmount] with Ordering[BitcoinAmount] {

    override def plus(x: BitcoinAmount, y: BitcoinAmount) = x + y

    override def times(x: BitcoinAmount, y: BitcoinAmount) = BitcoinAmount(x.units * y.units)

    override def minus(x: BitcoinAmount, y: BitcoinAmount) = x - y

    override def negate(x: BitcoinAmount): BitcoinAmount = -x

    override def quot(x: BitcoinAmount, y: BitcoinAmount) = BitcoinAmount(x.units / y.units)

    override def rem(x: BitcoinAmount, y: BitcoinAmount) = BitcoinAmount(x.units % y.units)

    override def toDouble(x: BitcoinAmount): Double = x.units.toDouble

    override def toFloat(x: BitcoinAmount): Float = x.units.toFloat

    override def toLong(x: BitcoinAmount): Long = x.units

    override def toInt(x: BitcoinAmount): Int = x.units.toInt

    override def fromInt(x: Int): BitcoinAmount = BitcoinAmount(x)

    override def compare(x: BitcoinAmount, y: BitcoinAmount): Int = x.units.compare(y.units)
  }

}
