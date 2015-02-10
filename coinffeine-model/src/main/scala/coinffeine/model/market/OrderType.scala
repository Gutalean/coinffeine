package coinffeine.model.market

import coinffeine.model.currency.FiatCurrency

sealed trait OrderType {
  def name: String
  def shortName: String
  def priceOrdering[C <: FiatCurrency]: Ordering[Price[C]]
  def oppositeType: OrderType

  override def toString = name
}

object OrderType {
  val values = Seq(Bid, Ask)
}

/** Trying to buy bitcoins */
case object Bid extends OrderType {
  override val name = "Bid (buy)"
  override val shortName = "bid"
  override def priceOrdering[C <: FiatCurrency] = Ordering.by[Price[C], BigDecimal](x => -x.value)
  override def oppositeType = Ask
}

/** Trying to sell bitcoins */
case object Ask extends OrderType {
  override val name = "Ask (sell)"
  override val shortName = "ask"
  override def priceOrdering[C <: FiatCurrency] = Ordering.by[Price[C], BigDecimal](_.value)
  override def oppositeType = Bid
}
