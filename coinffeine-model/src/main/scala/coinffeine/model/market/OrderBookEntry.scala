package coinffeine.model.market

import coinffeine.model.currency.{Bitcoin, FiatCurrency}

/** Request for an interchange. */
case class OrderBookEntry[C <: FiatCurrency](
    id: OrderId, orderType: OrderType, amount: Bitcoin.Amount, price: OrderPrice[C]) {
  require(amount.isPositive, "Amount ordered must be strictly positive")

  override def toString = "%s(%s, %s at %s)".format(
    if (orderType == Bid) "Bid" else "Ask", id, amount, price)
}

object OrderBookEntry {

  /** Gets the natural order for entries on a given currency. */
  def ordering[C <: FiatCurrency](currency: C): Ordering[OrderBookEntry[C]] = {
    def lessPriceThan(left: Option[BigDecimal], right: Option[BigDecimal]): Boolean =
      (left, right) match {
        case (_, None) => false
        case (None, _) => true
        case (Some(leftPrice), Some(rightPrice)) => leftPrice < rightPrice
      }

    Ordering.fromLessThan[OrderBookEntry[C]] { (leftEntry, rightEntry) =>
      (leftEntry.orderType, rightEntry.orderType) match {
        case (Bid, Ask) => true
        case (Ask, Bid) => false
        case (Ask, Ask) =>
          lessPriceThan(leftEntry.price.toOption.map(_.value), rightEntry.price.toOption.map(_.value))
        case (Bid, Bid) =>
          lessPriceThan(leftEntry.price.toOption.map(-_.value), rightEntry.price.toOption.map(-_.value))
      }
    }
  }

  def apply[C <: FiatCurrency](id: OrderId,
                               orderType: OrderType,
                               amount: Bitcoin.Amount,
                               limit: Price[C]): OrderBookEntry[C] =
    OrderBookEntry(id, orderType, amount, LimitPrice(limit))

  /** Creates an entry with a random identifier */
  def random[C <: FiatCurrency](orderType: OrderType,
                                amount: Bitcoin.Amount,
                                limit: Price[C]): OrderBookEntry[C] =
    random(orderType, amount, LimitPrice(limit))

  /** Creates an entry with a random identifier */
  def random[C <: FiatCurrency](orderType: OrderType,
                                amount: Bitcoin.Amount,
                                price: OrderPrice[C]): OrderBookEntry[C] =
    OrderBookEntry(OrderId.random(), orderType, amount, price)

  def fromOrder[C <: FiatCurrency](order: Order[C]): OrderBookEntry[C] =
    OrderBookEntry(order.id, order.orderType, order.amount, order.price)
}
