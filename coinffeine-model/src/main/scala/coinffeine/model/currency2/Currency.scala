package coinffeine.model.currency2

import scala.math.BigDecimal.RoundingMode

/** Representation of a currency. */
trait Currency {

  /** Shorthand for the type of amounts of money of this currency */
  type Amount <: CurrencyAmount[Amount]

  /** Minimum amount that can be expressed on this currency in terms of decimal positions allowed */
  val precision: Int

  val symbol: String

  val preferredSymbolPosition: Currency.SymbolPosition

  lazy val zero: Amount = apply(0)

  lazy val smallestAmount: Amount = fromUnits(1)

  lazy val unitsInOne: Long = Seq.fill(precision)(10).product

  def fromUnits(units: Long): Amount

  def closestAmount(value: BigDecimal): Amount = roundAmount(value, RoundingMode.HALF_EVEN)

  def exactAmount(value: BigDecimal): Amount = roundAmount(value, RoundingMode.UNNECESSARY)

  private def roundAmount(value: BigDecimal, roundingMode: RoundingMode.Value): Amount = {
    val units = value.setScale(precision, roundingMode) * unitsInOne
    fromUnits(units.toLong)
  }

  def apply(value: BigDecimal): Amount = exactAmount(value)
  def apply(value: Int): Amount = exactAmount(value)
  def apply(value: Double): Amount = exactAmount(value)
  def apply(value: java.math.BigDecimal): Amount = exactAmount(BigDecimal(value))

  def isValidAmount(value: BigDecimal): Boolean = (value * unitsInOne).isWhole()

  def toString: String
}

object Currency {

  sealed trait SymbolPosition
  case object SymbolPrefixed extends SymbolPosition
  case object SymbolSuffixed extends SymbolPosition
  case object NoSymbol extends SymbolPosition
}
