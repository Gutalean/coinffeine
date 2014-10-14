package coinffeine.peer.market.orders.controller

import org.scalatest.Assertions

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{OrderStatus, Order}
import coinffeine.protocol.messages.brokerage.OrderMatch

class MockOrderControllerListener[C <: FiatCurrency]
  extends OrderController.Listener[C] with Assertions {

  var currentOrder: Order[C] = _
  var orderMatchResolutions = Seq.empty[MatchResult[C]]

  override def onOrderChange(oldOrder: Order[C], newOrder: Order[C]): Unit = {
    currentOrder = newOrder
  }

  override def onOrderMatchResolution(orderMatch: OrderMatch[C], result: MatchResult[C]): Unit = {
    orderMatchResolutions :+= result
  }

  def lastStatus: OrderStatus = {
    require(currentOrder != null, "Order status was never updated")
    currentOrder.status
  }

  def lastMatchResolution: MatchResult[C] =
    orderMatchResolutions.headOption.getOrElse(fail("No order match was resolved"))
}
