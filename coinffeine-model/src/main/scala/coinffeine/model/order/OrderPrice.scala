package coinffeine.model.order

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}

/** Pricing strategy for an order */
sealed trait OrderPrice[C <: FiatCurrency] {
  def isLimited: Boolean
  def toOption: Option[Price[C]]
  def currency: C

  def outbids(otherPrice: OrderPrice[C]): Boolean = (this, otherPrice) match {
    case (_, MarketPrice(_)) => false
    case (MarketPrice(_), _) => true
    case (LimitPrice(left), LimitPrice(right)) => left.outbids(right)
  }

  def outbidsOrMatches(otherPrice: OrderPrice[C]): Boolean =
    this == otherPrice || this.outbids(otherPrice)

  def underbids(otherPrice: OrderPrice[C]): Boolean = !this.outbidsOrMatches(otherPrice)

  def underbidsOrMatches(otherPrice: OrderPrice[C]): Boolean = !this.outbids(otherPrice)
}

/** Accept the current market price */
case class MarketPrice[C <: FiatCurrency](override val currency: C) extends OrderPrice[C] {
  override def isLimited = false
  override def toOption = None
}

/** Accept any price up to a limit, wait on the order book if no compatible counterpart price
  * is available. This is a maximum price for bid orders and a minimum one for asks.
  */
case class LimitPrice[C <: FiatCurrency](limit: Price[C]) extends OrderPrice[C] {
  override def isLimited = true
  override def toOption = Some(limit)
  override def currency = limit.currency
}

object LimitPrice {
  /** Convenience factory method to create limit prices from a concrete amount of fiat */
  def apply[C <: FiatCurrency](amount: CurrencyAmount[C]): LimitPrice[C] = LimitPrice(Price(amount))
}


