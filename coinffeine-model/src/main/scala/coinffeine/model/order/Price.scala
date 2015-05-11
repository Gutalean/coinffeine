package coinffeine.model.order

import scala.math.BigDecimal.RoundingMode

import coinffeine.model.currency.{Bitcoin, CurrencyAmount, FiatCurrency}

/** Bitcoin price with respect to a given fiat currency.
  *
  * Not that this price is not limited by the precision of either Bitcoin or the corresponding
  * currency.
  */
case class Price[C <: FiatCurrency](value: BigDecimal, currency: C) {
  require(value > 0, s"Price must be strictly positive ($value given)")

  def outbids(otherPrice: Price[C]): Boolean = value > otherPrice.value
  def outbidsOrMatches(otherPrice: Price[C]): Boolean = value >= otherPrice.value

  def underbids(otherPrice: Price[C]): Boolean = value < otherPrice.value
  def underbidsOrMatches(otherPrice: Price[C]): Boolean = value <= otherPrice.value

  def averageWith(otherPrice: Price[C]): Price[C] = copy(value = (value + otherPrice.value) / 2)

  def scaleBy(factor: BigDecimal): Price[C] = copy(value = value * factor)

  /** Price of a given bitcoin amount. The result is rounded to the precision of the currency. */
  def of(amount: Bitcoin.Amount): CurrencyAmount[C] = {
    require(amount.isPositive, s"Cannot price a non-positive amount ($amount given)")
    val closestAmount = CurrencyAmount.closestAmount(value * amount.value, currency)
    closestAmount max CurrencyAmount.smallestAmount(currency)
  }

  override def toString = s"$normalisedValue $currency/BTC"

  private def normalisedValue = value.setScale(currency.precision, RoundingMode.UP)
}

object Price {
  def apply[C <: FiatCurrency](price: CurrencyAmount[C]): Price[C] =
    Price(price.value, price.currency)

  def whenExchanging[C <: FiatCurrency](bitcoinAmount: Bitcoin.Amount,
                                        fiatAmount: CurrencyAmount[C]): Price[C] =
    Price(fiatAmount.value / bitcoinAmount.value, fiatAmount.currency)
}
