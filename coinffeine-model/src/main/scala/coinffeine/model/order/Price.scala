package coinffeine.model.order

import scala.math.BigDecimal.RoundingMode

import coinffeine.model.currency.{BitcoinAmount, FiatAmount, FiatCurrency}

/** Bitcoin price with respect to a given fiat currency.
  *
  * Not that this price is not limited by the precision of either Bitcoin or the corresponding
  * currency.
  */
case class Price(value: BigDecimal, currency: FiatCurrency) {
  require(value > 0, s"Price must be strictly positive ($value given)")

  def outbids(otherPrice: Price): Boolean = value > otherPrice.value
  def outbidsOrMatches(otherPrice: Price): Boolean = value >= otherPrice.value

  def underbids(otherPrice: Price): Boolean = value < otherPrice.value
  def underbidsOrMatches(otherPrice: Price): Boolean = value <= otherPrice.value

  def averageWith(otherPrice: Price): Price = copy(value = (value + otherPrice.value) / 2)

  def scaleBy(factor: BigDecimal): Price = copy(value = value * factor)

  /** Price of a given bitcoin amount. The result is rounded to the precision of the currency. */
  def of(amount: BitcoinAmount): FiatAmount = {
    require(amount.isPositive, s"Cannot price a non-positive amount ($amount given)")
    val closestAmount = currency.closestAmount(value * amount.value)
    closestAmount max currency.smallestAmount
  }

  override def toString = s"$normalisedValue $currency/BTC"

  private def normalisedValue = value.setScale(currency.precision, RoundingMode.UP)
}

object Price {
  def apply(price: FiatAmount): Price =
    Price(price.value, price.currency)

  def whenExchanging(bitcoinAmount: BitcoinAmount, fiatAmount: FiatAmount): Price =
    Price(fiatAmount.value / bitcoinAmount.value, fiatAmount.currency)
}
