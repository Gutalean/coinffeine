package coinffeine.model.currency

import java.math.BigInteger
import java.util.{Currency => JavaCurrency}

/** Representation of a currency. */
trait Currency {

  /** The instance of the concrete type that extends this trait. */
  val self: this.type = this

  /** Minimum amount that can be expressed on this currency in terms of decimal positions allowed */
  val precision: Int

  /** An amount of currency.
    *
    * Please note this is a path-dependent type. It cannot be used as Currency.Amount but as UsDollar.Amount,
    * Euros.Amount, etc. If you want to use currency value in a polymorphic way, please use CurrencyAmount.
    *
    * @param value The value represented by this amount.
    */
  def amount(value: BigDecimal): CurrencyAmount[this.type] = CurrencyAmount(value, self)

  def isValidAmount(value: BigDecimal): Boolean =
    (value * BigDecimal(10).pow(precision)).isWhole()

  def apply(value: BigDecimal) = amount(value)
  def apply(value: Int) = amount(value)
  def apply(value: Double) = amount(value)
  def apply(value: java.math.BigDecimal) = amount(BigDecimal(value))

  def toString: String

  lazy val Zero: CurrencyAmount[this.type] = apply(0)
}

object Currency {

  object UsDollar extends FiatCurrency {
    val javaCurrency = JavaCurrency.getInstance("USD")
    override val precision = 2
  }

  object Euro extends FiatCurrency {
    val javaCurrency = JavaCurrency.getInstance("EUR")
    override val precision = 2
  }

  object Bitcoin extends Currency {
    val OneBtcInSatoshi = BigDecimal(100000000)
    override val precision = 8
    override val toString = "BTC"

    def fromSatoshi(amount: BigInteger) = Bitcoin(BigDecimal(amount) / OneBtcInSatoshi)
  }
}

