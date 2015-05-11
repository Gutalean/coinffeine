package coinffeine.model.order

import org.joda.time.DateTime

import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.market.Spread

case class OrderRequest[C <: FiatCurrency](orderType: OrderType,
                                           amount: Bitcoin.Amount,
                                           price: OrderPrice[C]) {
  require(amount.isPositive, s"Cannot open orders with non-positive amounts: $amount given")

  def create(): ActiveOrder[C] = ActiveOrder.random(orderType, amount, price, timestamp = DateTime.now())

  def estimatedPrice(spread: Spread[C]): Option[Price[C]] =
    price.toOption orElse (orderType match {
      case Bid => spread.lowestAsk
      case Ask => spread.highestBid
    })
}
