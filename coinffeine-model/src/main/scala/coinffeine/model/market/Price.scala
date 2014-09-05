package coinffeine.model.market

import scala.math.BigDecimal.RoundingMode

import coinffeine.model.currency.{BitcoinAmount, CurrencyAmount, FiatCurrency}

/** Bitcoin price with respect to a given fiat currency.
  *
  * Not that this price is not limited by the precision of either Bitcoin or the corresponding
  * currency.
  */
case class Price[C <: FiatCurrency](value: BigDecimal, currency: C) {
  require(value > 0, "Price must be strictly positive")

  def outbidsOrMatches(otherPrice: Price[C]): Boolean = value >= otherPrice.value

  def underbids(otherPrice: Price[C]): Boolean = value < otherPrice.value

  def averageWith(otherPrice: Price[C]): Price[C] = copy(value = (value + otherPrice.value) / 2)

  /** Price of a given bitcoin amount. The result is rounded to the precision of the currency. */
  def of(amount: BitcoinAmount): CurrencyAmount[C] = {
    require(amount.isPositive, s"Cannot price a non-positive amount ($amount given)")
    val exactAmount = value * amount.value
    val roundedAmount = exactAmount.setScale(currency.precision, RoundingMode.HALF_EVEN)
    if (roundedAmount.signum > 0) CurrencyAmount(roundedAmount, currency)
    else CurrencyAmount.smallestAmount(currency)
  }

  override def toString = s"$value $currency/BTC"
}

object Price {
  def apply[C <: FiatCurrency](price: CurrencyAmount[C]): Price[C] =
    Price(price.value, price.currency)
}
