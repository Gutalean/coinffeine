package coinffeine.peer.market.orders.controller

import org.scalatest.Assertions

import coinffeine.model.order.{ActiveOrder, OrderStatus}

class MockOrderControllerListener extends OrderController.Listener with Assertions {

  var currentOrder: ActiveOrder = _

  override def onOrderChange(oldOrder: ActiveOrder, newOrder: ActiveOrder): Unit = {
    currentOrder = newOrder
  }

  def lastStatus: OrderStatus = lastOrder.status

  def lastOrder: ActiveOrder = {
    require(currentOrder != null, "Order status was never updated")
    currentOrder
  }

  def inMarket: Boolean = currentOrder.shouldBeOnMarket
}
