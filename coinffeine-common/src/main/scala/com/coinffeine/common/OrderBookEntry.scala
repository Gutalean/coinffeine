package com.coinffeine.common

/** Request for an interchange. */
case class OrderBookEntry[+F <: FiatAmount](
    id: OrderId, orderType: OrderType, amount: BitcoinAmount, price: F) {
  require(amount.isPositive, "Amount ordered must be strictly positive")
  require(price.isPositive, "Price must be strictly positive")
}

object OrderBookEntry {

  /** Gets the natural order for entries on a given currency. */
  def ordering[C <: FiatCurrency](currency: C): Ordering[OrderBookEntry[CurrencyAmount[C]]] =
    Ordering.by[OrderBookEntry[CurrencyAmount[C]], BigDecimal] {
      case OrderBookEntry(_, Bid, _, price) => -price.value
      case OrderBookEntry(_, Ask, _, price) => price.value
    }

  /** Creates an entry with a random identifier */
  def apply[F <: FiatAmount](orderType: OrderType,
                             amount: BitcoinAmount, price: F): OrderBookEntry[F] =
    OrderBookEntry(OrderId.random(), orderType, amount, price)
}
