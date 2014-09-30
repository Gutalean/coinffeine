package coinffeine.model.currency

/** Representation of a currency. */
trait Currency {

  /** Shorthand for the type of amounts of money of this currency */
  type Amount = CurrencyAmount[this.type]

  lazy val numeric: Integral[Amount] with Ordering[Amount] =
    new IntegralCurrencyAmount[this.type](this)

  /** Minimum amount that can be expressed on this currency in terms of decimal positions allowed */
  val precision: Int

  /** An amount of currency.
    *
    * Please note this is a path-dependent type. It cannot be used as Currency.Amount but as UsDollar.Amount,
    * Euros.Amount, etc. If you want to use currency value in a polymorphic way, please use CurrencyAmount.
    *
    * @param value The value represented by this amount.
    */
  def amount(value: BigDecimal): Amount = CurrencyAmount(value, this)

  def isValidAmount(value: BigDecimal): Boolean =
    (value * BigDecimal(10).pow(precision)).isWhole()

  def apply(value: BigDecimal): Amount = amount(value)
  def apply(value: Int): Amount = amount(value)
  def apply(value: Double): Amount = amount(value)
  def apply(value: java.math.BigDecimal): Amount = amount(BigDecimal(value))

  def toString: String

  lazy val Zero: Amount = apply(0)
}
