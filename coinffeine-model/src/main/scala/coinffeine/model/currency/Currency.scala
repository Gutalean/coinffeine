package coinffeine.model.currency

/** Representation of a currency. */
trait Currency {

  /** Shorthand for the type of amounts of money of this currency */
  type Amount = CurrencyAmount[this.type]

  lazy val numeric: Integral[Amount] with Ordering[Amount] =
    new IntegralCurrencyAmount[this.type](this)

  /** Minimum amount that can be expressed on this currency in terms of decimal positions allowed */
  val precision: Int

  def closestAmount(value: BigDecimal): Amount =
    CurrencyAmount.closestAmount[this.type](value, this)
  def exactAmount(value: BigDecimal): Amount =
    CurrencyAmount.exactAmount[this.type](value, this)

  def isValidAmount(value: BigDecimal): Boolean = (value * UnitsInOne).isWhole()

  def apply(value: BigDecimal): Amount = exactAmount(value)
  def apply(value: Int): Amount = exactAmount(value)
  def apply(value: Double): Amount = exactAmount(value)
  def apply(value: java.math.BigDecimal): Amount = exactAmount(BigDecimal(value))

  def toString: String

  lazy val Zero: Amount = apply(0)
  lazy val UnitsInOne: Long = Seq.fill(precision)(10).product
}
