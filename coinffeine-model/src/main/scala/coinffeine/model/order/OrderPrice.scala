package coinffeine.model.order

import coinffeine.model.currency.{FiatAmount, FiatCurrency}

/** Pricing strategy for an order */
sealed trait OrderPrice {
  def isLimited: Boolean
  def toOption: Option[Price]
  def currency: FiatCurrency

  def outbids(otherPrice: OrderPrice): Boolean = (this, otherPrice) match {
    case (_, MarketPrice(_)) => false
    case (MarketPrice(_), _) => true
    case (LimitPrice(left), LimitPrice(right)) => left.outbids(right)
  }

  def outbidsOrMatches(otherPrice: OrderPrice): Boolean =
    this == otherPrice || this.outbids(otherPrice)

  def underbids(otherPrice: OrderPrice): Boolean = !this.outbidsOrMatches(otherPrice)

  def underbidsOrMatches(otherPrice: OrderPrice): Boolean = !this.outbids(otherPrice)
}

/** Accept the current market price */
case class MarketPrice(override val currency: FiatCurrency) extends OrderPrice {
  override def isLimited = false
  override def toOption = None
}

/** Accept any price up to a limit, wait on the order book if no compatible counterpart price
  * is available. This is a maximum price for bid orders and a minimum one for asks.
  */
case class LimitPrice(limit: Price) extends OrderPrice {
  override def isLimited = true
  override def toOption = Some(limit)
  override def currency = limit.currency
}

object LimitPrice {
  /** Convenience factory method to create limit prices from a concrete amount of fiat */
  def apply(amount: FiatAmount): LimitPrice = LimitPrice(Price(amount))
}


