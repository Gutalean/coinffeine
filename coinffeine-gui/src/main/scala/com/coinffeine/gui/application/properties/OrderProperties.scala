package com.coinffeine.gui.application.properties

import com.coinffeine.common._

import scalafx.beans.property.ObjectProperty

// TODO: use an Order instead of an OrderBookEntry
case class OrderProperties(order: OrderBookEntry[FiatAmount]) {
  val orderTypeProperty = new ObjectProperty[OrderType](this, "orderType", order.orderType)
  val amountProperty = new ObjectProperty[BitcoinAmount](this, "amount", order.amount)
  val priceProperty = new ObjectProperty[FiatAmount](this, "price", order.price)
}
