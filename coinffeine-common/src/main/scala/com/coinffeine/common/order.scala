package com.coinffeine.common

sealed trait OrderType {
  def name: String
  def priceOrdering[C <: FiatCurrency]: Ordering[CurrencyAmount[C]]

  override def toString = name
}

/** Trying to buy bitcoins */
case object Bid extends OrderType {
  override val name = "Bid (buy)"
  override def priceOrdering[C <: FiatCurrency] = Ordering.by[CurrencyAmount[C], BigDecimal](x => -x.value)
}

/** Trying to sell bitcoins */
case object Ask extends OrderType {
  override val name = "Ask (sell)"
  override def priceOrdering[C <: FiatCurrency] = Ordering.by[CurrencyAmount[C], BigDecimal](_.value)
}

/** Request for an interchange. */
case class Order[+F <: FiatAmount](
    id: OrderId, orderType: OrderType, amount: BitcoinAmount, price: F) {
  require(amount.isPositive, "Amount ordered must be strictly positive")
  require(price.isPositive, "Price must be strictly positive")
}

object Order {
  /** Gets the natural order for orders on a given currency. */
  def ordering[C <: FiatCurrency](currency: C): Ordering[Order[CurrencyAmount[C]]] =
    Ordering.by[Order[CurrencyAmount[C]], BigDecimal] {
      case Order(_, Bid, _, price) => -price.value
      case Order(_, Ask, _, price) => price.value
    }

  def apply[F <: FiatAmount](orderType: OrderType, amount: BitcoinAmount, price: F): Order[F] =
    Order(OrderId.random(), orderType, amount, price)
}
