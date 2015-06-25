package coinffeine.model.currency

trait CurrencyAmount[Self <: CurrencyAmount[Self]] extends Ordered[Self] { this: Self =>

  require(currency.isValidAmount(value), s"Invalid amount for $currency: $value")

  val units: Long

  val currency: Currency

  lazy val value: BigDecimal = BigDecimal(units) / currency.unitsInOne

  def +(other: Self): Self = fromUnits(units + other.units)

  def -(other: Self): Self = fromUnits(units - other.units)

  def *(mult: Long): Self = fromUnits(units * mult)

  def *(mult: BigDecimal): Self = fromExactValue(value * mult)

  def /(divisor: Long): Self = {
    require(units % divisor == 0, "Division with remainder")
    fromUnits(units / divisor)
  }

  def /%(other: Self): (Long, Self) = (units / other.units, fromUnits(units % other.units))

  def unary_- : Self = fromUnits(-units)

  def min(that: Self): Self = if (this.units <= that.units) this else that

  def max(that: Self): Self = if (this.units >= that.units) this else that

  def averageWith(that: Self): Self = fromUnits((this.units + that.units) / 2)

  val isPositive = units > 0
  val isNegative = units < 0

  override def compare(other: Self): Int = {
    require(currency == other.currency)
    units.compareTo(other.units)
  }

  def format(symbolPos: Currency.SymbolPosition): String =
    CurrencyAmount.format(this, symbolPos)

  def format: String = CurrencyAmount.format(this)

  override def toString = format

  protected def fromUnits(units: Long): Self
  
  protected def fromExactValue(value: BigDecimal): Self
}

object CurrencyAmount {

  def format(
      amount: CurrencyAmount[_],
      symbolPos: Currency.SymbolPosition): String = {
    val currency = amount.currency
    val units = amount.units
    val numbers = s"%s%d.%0${currency.precision}d".format(
      if (units < 0) "-" else "", units.abs / currency.unitsInOne, units.abs % currency.unitsInOne)
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
