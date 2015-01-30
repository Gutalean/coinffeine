package coinffeine.model.market

import coinffeine.model.currency.FiatCurrency

/** Pricing strategy for an order */
sealed trait OrderPrice[C <: FiatCurrency] {
  def isLimited: Boolean
  def toOption: Option[Price[C]]
  def currency: C
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


