package coinffeine.model.market

import coinffeine.model.currency.{BitcoinAmount, FiatCurrency}

/** Request for an interchange. */
case class OrderBookEntry[C <: FiatCurrency](
    id: OrderId, orderType: OrderType, amount: BitcoinAmount, price: Price[C]) {
  require(amount.isPositive, "Amount ordered must be strictly positive")
}

object OrderBookEntry {

  /** Gets the natural order for entries on a given currency. */
  def ordering[C <: FiatCurrency](currency: C): Ordering[OrderBookEntry[C]] =
    Ordering.by[OrderBookEntry[C], BigDecimal] {
      case OrderBookEntry(_, Bid, _, price) => -price.value
      case OrderBookEntry(_, Ask, _, price) => price.value
    }

  /** Creates an entry with a random identifier */
  def apply[C <: FiatCurrency](orderType: OrderType,
                               amount: BitcoinAmount,
                               price: Price[C]): OrderBookEntry[C] =
    OrderBookEntry(OrderId.random(), orderType, amount, price)

  def fromOrder[C <: FiatCurrency](order: Order[C]): OrderBookEntry[C] =
    OrderBookEntry(order.id, order.orderType, order.amount, order.price)
}
