package coinffeine.peer.market.orders.controller

import org.scalatest.Assertions

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{OrderStatus, ActiveOrder}

class MockOrderControllerListener[C <: FiatCurrency]
  extends OrderController.Listener[C] with Assertions {

  var currentOrder: ActiveOrder[C] = _

  override def onOrderChange(oldOrder: ActiveOrder[C], newOrder: ActiveOrder[C]): Unit = {
    currentOrder = newOrder
  }

  def lastStatus: OrderStatus = lastOrder.status

  def lastOrder: ActiveOrder[C] = {
    require(currentOrder != null, "Order status was never updated")
    currentOrder
  }

  def inMarket: Boolean = currentOrder.shouldBeOnMarket
}
