package coinffeine.model.currency

trait CurrencyAmount[Self <: CurrencyAmount[Self]] extends Ordered[Self] { this: Self =>

  require(currency.isValidAmount(value), s"Invalid amount for $currency: $value")

  val units: Long

  val currency: Currency

  lazy val value: BigDecimal = BigDecimal(units) / currency.unitsInOne

  def +(other: Self): Self = binaryOperation(other)(_ + _)

  def -(other: Self): Self = binaryOperation(other)(_ - _)

  def *(mult: Long): Self = fromUnits(units * mult)

  def *(mult: BigDecimal): Self = fromExactValue(value * mult)

  def /(divisor: Long): Self = {
    require(units % divisor == 0, "Division with remainder")
    fromUnits(units / divisor)
  }

  def /%(other: Self): (Long, Self) = (units / other.units, binaryOperation(other)(_ % _))

  def unary_- : Self = fromUnits(-units)

  def abs: Self = fromUnits(Math.abs(units))

  def min(other: Self): Self = {
    requireSameCurrency(other)
    if (this.units <= other.units) this else other
  }

  def max(other: Self): Self = {
    requireSameCurrency(other)
    if (this.units >= other.units) this else other
  }

  def averageWith(other: Self): Self = binaryOperation(other) { (left, right) =>
    (left + right) / 2
  }

  val isPositive = units > 0
  val isNegative = units < 0

  override def compare(other: Self): Int = {
    requireSameCurrency(other)
    units.compareTo(other.units)
  }

  def format(symbolPos: Currency.SymbolPosition): String =
    CurrencyAmountFormatter.format(this, symbolPos)

  override def toString = CurrencyAmountFormatter.format(this)

  protected def fromUnits(units: Long): Self

  protected def fromExactValue(value: BigDecimal): Self

  private def binaryOperation(other: Self)(op: (Long, Long) => Long): Self = {
    requireSameCurrency(other)
    fromUnits(op(units, other.units))
  }

  private def requireSameCurrency(other: Self): Unit = {
    require(currency == other.currency, s"Cannot operate $currency with ${other.currency}")
  }
}
