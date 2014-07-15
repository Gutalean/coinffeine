package com.coinffeine.gui.application.properties

import coinffeine.model.order.{OrderType, OrderBookEntry}

import scalafx.beans.property.ObjectProperty

import coinffeine.model.currency.{BitcoinAmount, FiatAmount}
import com.coinffeine.common._

// TODO: use an Order instead of an OrderBookEntry
case class OrderProperties(order: OrderBookEntry[FiatAmount]) {
  val orderTypeProperty = new ObjectProperty[OrderType](this, "orderType", order.orderType)
  val amountProperty = new ObjectProperty[BitcoinAmount](this, "amount", order.amount)
  val priceProperty = new ObjectProperty[FiatAmount](this, "price", order.price)
}
