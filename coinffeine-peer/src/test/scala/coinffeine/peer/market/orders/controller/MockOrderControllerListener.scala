package coinffeine.peer.market.orders.controller

import org.scalatest.Assertions

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{OrderStatus, Order}

class MockOrderControllerListener[C <: FiatCurrency]
  extends OrderController.Listener[C] with Assertions {

  var currentOrder: Order[C] = _
  var inMarket = false

  override def onOrderChange(oldOrder: Order[C], newOrder: Order[C]): Unit = {
    currentOrder = newOrder
  }

  override def keepOffMarket(): Unit = {
    inMarket = false
  }

  override def keepInMarket(): Unit = {
    inMarket = true
  }

  def lastStatus: OrderStatus = {
    require(currentOrder != null, "Order status was never updated")
    currentOrder.status
  }

  def isInMarket: Boolean = inMarket
}
