package coinffeine.model.market

import coinffeine.model.currency.{BitcoinAmount, FiatCurrency}
import coinffeine.model.order._

/** Request for an interchange. */
case class OrderBookEntry(
    id: OrderId, orderType: OrderType, amount: BitcoinAmount, price: OrderPrice) {
  require(amount.isPositive, "Amount ordered must be strictly positive")

  override def toString = "%s(%s, %s at %s)".format(
    if (orderType == Bid) "Bid" else "Ask", id, amount, price)
}

object OrderBookEntry {

  /** Gets the natural order for entries on a given currency. */
  def ordering(currency: FiatCurrency): Ordering[OrderBookEntry] = {
    def lessPriceThan(left: Option[BigDecimal], right: Option[BigDecimal]): Boolean =
      (left, right) match {
        case (_, None) => false
        case (None, _) => true
        case (Some(leftPrice), Some(rightPrice)) => leftPrice < rightPrice
      }

    Ordering.fromLessThan[OrderBookEntry] { (leftEntry, rightEntry) =>
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

  def apply(
    id: OrderId,
    orderType: OrderType,
    amount: BitcoinAmount,
    limit: Price): OrderBookEntry = OrderBookEntry(id, orderType, amount, LimitPrice(limit))

  /** Creates an entry with a random identifier */
  def random(orderType: OrderType, amount: BitcoinAmount, limit: Price): OrderBookEntry =
    random(orderType, amount, LimitPrice(limit))

  /** Creates an entry with a random identifier */
  def random(orderType: OrderType, amount: BitcoinAmount, price: OrderPrice): OrderBookEntry =
    OrderBookEntry(OrderId.random(), orderType, amount, price)

  def fromOrder(order: Order): OrderBookEntry =
    OrderBookEntry(order.id, order.orderType, order.amount, order.price)
}
