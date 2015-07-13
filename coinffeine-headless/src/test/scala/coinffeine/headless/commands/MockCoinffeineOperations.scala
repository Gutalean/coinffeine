package coinffeine.headless.commands

import coinffeine.common.properties.MutablePropertyMap
import coinffeine.model.order.{ActiveOrder, Order, OrderId}

class MockCoinffeineOperations extends DummyCoinffeineOperations {

  override val orders = new MutablePropertyMap[OrderId, Order]

  def givenOrderExists(order: ActiveOrder): Unit = {
    orders.set(order.id, order)
  }

  private var _cancellations = Seq.empty[OrderId]

  def cancellations: Seq[OrderId] = _cancellations

  override def cancelOrder(order: OrderId): Unit = synchronized { _cancellations :+= order }
}
