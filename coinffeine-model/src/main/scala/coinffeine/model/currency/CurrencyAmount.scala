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
    CurrencyAmount.format(this, symbolPos)

  def format: String = CurrencyAmount.format(this)

  override def toString = format

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

object CurrencyAmount {

  def format(
      amount: CurrencyAmount[_],
      symbolPos: Currency.SymbolPosition): String = {
    val currency = amount.currency
    val units = amount.units
    val numbers = {
      val formatString = s"%s%d.%0${currency.precision}d"
      formatString.format(
        if (units < 0) "-" else "",
        units.abs / currency.unitsInOne,
        units.abs % currency.unitsInOne)
    }
    addSymbol(numbers, symbolPos, currency)
  }

  def format(amount: CurrencyAmount[_]): String =
    format(amount, amount.currency.preferredSymbolPosition)

  def formatMissing[C <: Currency](currency: C, symbolPos: Currency.SymbolPosition): String = {
    val amount = "_." + "_" * currency.precision
    addSymbol(amount, symbolPos, currency)
  }

  def formatMissing[C <: Currency](currency: C): String =
    formatMissing(currency, currency.preferredSymbolPosition)

  private def addSymbol[C <: Currency](
       amount: String, symbolPos: Currency.SymbolPosition, currency: C): String = symbolPos match {
    case Currency.SymbolPrefixed => s"${currency.symbol}$amount"
    case Currency.SymbolSuffixed => s"$amount${currency.symbol}"
    case Currency.NoSymbol => amount
  }
}
