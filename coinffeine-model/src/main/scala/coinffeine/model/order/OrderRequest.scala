package coinffeine.model.order

import org.joda.time.DateTime

import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.market.Spread

case class OrderRequest(
    orderType: OrderType,
    amount: BitcoinAmount,
    price: OrderPrice) {
  require(amount.isPositive, s"Cannot open orders with non-positive amounts: $amount given")

  def create(): ActiveOrder =
    ActiveOrder.random(orderType, amount, price, timestamp = DateTime.now())

  def estimatedPrice(spread: Spread): Option[Price] =
    price.toOption orElse (orderType match {
      case Bid => spread.lowestAsk
      case Ask => spread.highestBid
    })
}
