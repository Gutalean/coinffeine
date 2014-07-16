package coinffeine.gui.application.properties

import scalafx.beans.property.ObjectProperty

import coinffeine.model.currency.{BitcoinAmount, FiatAmount}
import coinffeine.model.market.{OrderBookEntry, OrderType}

// TODO: use an Order instead of an OrderBookEntry
case class OrderProperties(order: OrderBookEntry[FiatAmount]) {
  val orderTypeProperty = new ObjectProperty[OrderType](this, "orderType", order.orderType)
  val amountProperty = new ObjectProperty[BitcoinAmount](this, "amount", order.amount)
  val priceProperty = new ObjectProperty[FiatAmount](this, "price", order.price)
}
