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
  * @tparam C The type of currency this amount is represented in
  */
case class CurrencyAmount[C <: Currency](value: BigDecimal, currency: C)
  extends PartiallyOrdered[CurrencyAmount[C]] {

  require(currency.isValidAmount(value),
    "Tried to create a currency amount which is invalid for that currency: " + this.toString)

  def +(other: CurrencyAmount[C]): CurrencyAmount[C] =
    copy(value = value + other.value)
  def -(other: CurrencyAmount[C]): CurrencyAmount[C] =
    copy(value = value - other.value)
  def * (mult: BigDecimal): CurrencyAmount[C] = copy(value = value * mult)
  def / (divisor: BigDecimal): CurrencyAmount[C] = copy(value = value / divisor)
  def /%(divisor: CurrencyAmount[C]): (Int, CurrencyAmount[C]) = {
    val (division, remainder) = value /% divisor.value
    (division.toIntExact, copy(remainder))
  }
  def unary_- : CurrencyAmount[C] = copy(value = -value)

  def min(that: CurrencyAmount[C]): CurrencyAmount[C] =
    if (this.value <= that.value) this else that
  def max(that: CurrencyAmount[C]): CurrencyAmount[C] =
    if (this.value >= that.value) this else that

  val isPositive = value > 0
  val isNegative = value < 0

  override def tryCompareTo[B >: CurrencyAmount[C] <% PartiallyOrdered[B]](that: B): Option[Int] =
    Try {
      val thatAmount = that.asInstanceOf[CurrencyAmount[_ <: FiatCurrency]]
      require(thatAmount.currency == this.currency)
      thatAmount
    }.toOption.map(thatAmount => this.value.compare(thatAmount.value))

  override def toString = value.toString() + " " + currency.toString
}

object CurrencyAmount {
  def zero[C <: Currency](currency: C): CurrencyAmount[C] = CurrencyAmount(0, currency)

  def smallestAmount[C <: Currency](currency: C) = {
    val smallestUnit = Seq.fill(currency.precision)(10).foldLeft(BigDecimal(1))(_ / _)
    CurrencyAmount(smallestUnit, currency)
  }

  def closestAmount[C <: Currency](value: BigDecimal, currency: C): CurrencyAmount[C] =
    CurrencyAmount(value.setScale(currency.precision, RoundingMode.HALF_EVEN), currency)
}
