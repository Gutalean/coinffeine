package coinffeine.headless.commands

import coinffeine.model.currency.Euro
import coinffeine.model.market._
import coinffeine.model.properties.MutablePropertyMap

class MockCoinffeineNetwork extends DummyCoinffeineNetwork {

  override val orders = new MutablePropertyMap[OrderId, AnyCurrencyOrder]

  def givenOrderExists(order: ActiveOrder[Euro.type]): Unit = {
    orders.set(order.id, order)
  }

  private var _cancellations = Seq.empty[OrderId]

  def cancellations: Seq[OrderId] = _cancellations

  override def cancelOrder(order: OrderId): Unit = synchronized { _cancellations :+= order }
}
