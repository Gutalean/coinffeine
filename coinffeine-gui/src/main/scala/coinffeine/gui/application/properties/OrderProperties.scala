package coinffeine.gui.application.properties

import scalafx.beans.property.{DoubleProperty, ObjectProperty}

import coinffeine.model.currency.{BitcoinAmount, FiatAmount, FiatCurrency}
import coinffeine.model.market.{Order, OrderId, OrderStatus, OrderType}
import coinffeine.model.network.PeerId

case class OrderProperties(order: Order[FiatCurrency]) {
  val idProperty = new ObjectProperty[OrderId](this, "id", order.id)
  val ownerProperty = new ObjectProperty[PeerId](this, "owner", order.owner)
  val orderTypeProperty = new ObjectProperty[OrderType](this, "orderType", order.orderType)
  val statusProperty = new ObjectProperty[OrderStatus](this, "status", order.status)
  val amountProperty = new ObjectProperty[BitcoinAmount](this, "amount", order.amount)
  val priceProperty = new ObjectProperty[FiatAmount](this, "price", order.price)
  val progressProperty = new DoubleProperty(this, "progress", order.progress)

  def update(order: Order[FiatCurrency]): Unit = {
    idProperty.set(order.id)
    ownerProperty.set(order.owner)
    orderTypeProperty.set(order.orderType)
    statusProperty.set(order.status)
    amountProperty.set(order.amount)
    priceProperty.set(order.price)
    progressProperty.set(order.progress)
  }
}
