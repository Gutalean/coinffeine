package coinffeine.model.currency

import scala.math.BigDecimal.RoundingMode
import scala.util.Try

/** An finite amount of currency C.
  *
  * This trait is used to grant polymorphism to currency amounts. You may combine it with a type
  * parameter in any function in order to accept generic currency amounts, as in:
  * {{{
  *   def myFunction[C <: Currency](amount: CurrencyAmount[C]): Unit { ... }
  * }}}
  *
  * @param units     Number of indivisible units on this currency amount
  * @param currency  Currency this amount is of
  * @tparam C The type of currency this amount is represented in
  */
case class CurrencyAmount[C <: Currency](units: Long, currency: C)
  extends PartiallyOrdered[CurrencyAmount[C]] {

  require(currency.isValidAmount(value), s"Invalid amount for $currency: $value")

  lazy val value: BigDecimal = BigDecimal(units) / currency.UnitsInOne

  def +(other: CurrencyAmount[C]): CurrencyAmount[C] = copy(units = units + other.units)
  def -(other: CurrencyAmount[C]): CurrencyAmount[C] = copy(units = units - other.units)
  def *(mult: Long): CurrencyAmount[C] = copy(units = units * mult)
  def *(mult: BigDecimal): CurrencyAmount[C] = CurrencyAmount.exactAmount(value * mult, currency)
  def /(divisor: Long): CurrencyAmount[C] = {
    require(units % divisor == 0, "Division with remainder")
    copy(units = units / divisor)
  }
  def /%(other: CurrencyAmount[C]): (Long, CurrencyAmount[C]) =
    (units / other.units, copy(units % other.units))
  def unary_- : CurrencyAmount[C] = copy(units = -units)

  def min(that: CurrencyAmount[C]): CurrencyAmount[C] =
    if (this.units <= that.units) this else that
  def max(that: CurrencyAmount[C]): CurrencyAmount[C] =
    if (this.units >= that.units) this else that
  def averageWith(that: CurrencyAmount[C]): CurrencyAmount[C] =
    CurrencyAmount((this.units + that.units) / 2, currency)

  val isPositive = units > 0
  val isNegative = units < 0

  /** Convert this amount to an integer number of the minimum indivisible units. This means
    * cents for Euro/Dollar and Satoshis for Bitcoin. */
  def toIndivisibleUnits: BigInt =
    (value / CurrencyAmount.smallestAmount(currency).value).toBigInt()

  override def tryCompareTo[B >: CurrencyAmount[C] <% PartiallyOrdered[B]](that: B): Option[Int] =
    Try {
      val thatAmount = that.asInstanceOf[CurrencyAmount[_ <: FiatCurrency]]
      require(thatAmount.currency == this.currency)
      thatAmount
    }.toOption.map(thatAmount => this.value.compare(thatAmount.value))

  def format(symbolPos: Currency.SymbolPosition): String = CurrencyAmount.format(this, symbolPos)

  def format: String = CurrencyAmount.format(this)

  override def toString = format

  def numeric: Integral[CurrencyAmount[C]] with Ordering[CurrencyAmount[C]] =
    currency.numeric.asInstanceOf[Integral[CurrencyAmount[C]] with Ordering[CurrencyAmount[C]]]
}

object CurrencyAmount {
  def zero[C <: Currency](currency: C): CurrencyAmount[C] = CurrencyAmount(0, currency)

  def smallestAmount[C <: Currency](currency: C) = CurrencyAmount(1, currency)

  def fromIndivisibleUnits[C <: Currency](units: Long, currency: C): CurrencyAmount[C] =
    CurrencyAmount(units, currency)

  def closestAmount[C <: Currency](value: BigDecimal, currency: C): CurrencyAmount[C] =
    toAmount(value, currency, RoundingMode.HALF_EVEN)

  @throws[ArithmeticException]("when the amount exceeds currency precision")
  def exactAmount[C <: Currency](value: BigDecimal, currency: C): CurrencyAmount[C] =
    toAmount(value, currency, RoundingMode.UNNECESSARY)

  private def toAmount[C <: Currency](value: BigDecimal,
                                      currency: C,
                                      roundingMode: RoundingMode.Value): CurrencyAmount[C] = {
    val units = value.setScale(currency.precision, roundingMode) * currency.UnitsInOne
    CurrencyAmount(units.toLong, currency)
  }

  def format[C <: Currency](amount: CurrencyAmount[C],
                            symbolPos: Currency.SymbolPosition): String = {
    val currency = amount.currency
    val units = amount.units
    val symbol = currency.symbol
    val numbers = s"%s%d.%0${currency.precision}d".format(
      if (units < 0) "-" else "", units.abs / currency.UnitsInOne, units.abs % currency.UnitsInOne)
    addSymbol(numbers, symbolPos, currency)
  }

  def format[C <: Currency](amount: CurrencyAmount[C]): String =
    format(amount, amount.currency.preferredSymbolPosition)

  def formatNone[C <: Currency](currency: C, symbolPos: Currency.SymbolPosition): String = {
    val amount = s"_.${"_" * currency.precision}"
    val symbol = currency.symbol
    addSymbol(amount, symbolPos, currency)
  }

  def formatNone[C <: Currency](currency: C): String =
    formatNone(currency, currency.preferredSymbolPosition)

  private def addSymbol[C <: Currency](
                                        amount: String,
                                        symbolPos: Currency.SymbolPosition,
                                        currency: C): String = symbolPos match {
    case Currency.SymbolPrefixed => s"${currency.symbol}$amount"
    case Currency.SymbolSuffixed => s"$amount${currency.symbol}"
    case Currency.NoSymbol => amount
  }
}
