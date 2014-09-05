package coinffeine.model.market

import coinffeine.model.currency.FiatCurrency

sealed trait OrderType {
  def name: String
  def priceOrdering[C <: FiatCurrency]: Ordering[Price[C]]

  override def toString = name
}

/** Trying to buy bitcoins */
case object Bid extends OrderType {
  override val name = "Bid (buy)"
  override def priceOrdering[C <: FiatCurrency] = Ordering.by[Price[C], BigDecimal](x => -x.value)
}

/** Trying to sell bitcoins */
case object Ask extends OrderType {
  override val name = "Ask (sell)"
  override def priceOrdering[C <: FiatCurrency] = Ordering.by[Price[C], BigDecimal](_.value)
}
