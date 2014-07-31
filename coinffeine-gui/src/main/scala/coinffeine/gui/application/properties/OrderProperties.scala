package coinffeine.gui.application.properties

import scalafx.beans.property.{DoubleProperty, ObjectProperty}

import coinffeine.model.currency.{BitcoinAmount, FiatAmount, FiatCurrency}
import coinffeine.model.market.{Order, OrderId, OrderStatus, OrderType}

class OrderProperties(order: Order[FiatCurrency]) {
  val idProperty = new ObjectProperty[OrderId](this, "id", order.id)
  val orderTypeProperty = new ObjectProperty[OrderType](this, "orderType", order.orderType)
  val statusProperty = new ObjectProperty[OrderStatus](this, "status", order.status)
  val amountProperty = new ObjectProperty[BitcoinAmount](this, "amount", order.amount)
  val priceProperty = new ObjectProperty[FiatAmount](this, "price", order.price)
  val progressProperty = new DoubleProperty(this, "progress", order.progress)

  def updateStatus(newStatus: OrderStatus): Unit = {
    statusProperty.set(newStatus)
  }

  def updateProgress(newProgress: Double): Unit = {
    progressProperty.set(newProgress)
  }
}
