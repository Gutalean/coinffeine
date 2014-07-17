package coinffeine.gui.application.properties

import scalafx.beans.property.ObjectProperty

import coinffeine.model.currency.{BitcoinAmount, FiatAmount, FiatCurrency}
import coinffeine.model.market.{Order, OrderType}

case class OrderProperties(order: Order[FiatCurrency]) {
  val orderTypeProperty = new ObjectProperty[OrderType](this, "orderType", order.orderType)
  val amountProperty = new ObjectProperty[BitcoinAmount](this, "amount", order.amount)
  val priceProperty = new ObjectProperty[FiatAmount](this, "price", order.price)
}
