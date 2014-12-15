package coinffeine.peer.api.mock

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{AnyCurrencyOrder, Order, OrderId}
import coinffeine.model.network.PeerId
import coinffeine.model.properties.{MutableProperty, MutablePropertyMap}
import coinffeine.peer.api.CoinffeineNetwork

class MockCoinffeineNetwork extends CoinffeineNetwork {

  override val activePeers: MutableProperty[Int] = new MutableProperty(0)
  override val brokerId: MutableProperty[Option[PeerId]] = new MutableProperty(None)
  override val orders: MutablePropertyMap[OrderId, AnyCurrencyOrder] = new MutablePropertyMap

  override def cancelOrder(orderId: OrderId): Unit = {
    orders.get(orderId).foreach(order => orders.set(orderId, order.cancel))
  }

  override def submitOrder[C <: FiatCurrency](order: Order[C]): Order[C] = {
    orders.set(order.id, order)
    order
  }
}
