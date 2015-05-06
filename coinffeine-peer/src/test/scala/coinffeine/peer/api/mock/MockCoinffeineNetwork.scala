package coinffeine.peer.api.mock

import org.joda.time.DateTime

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{AnyCurrencyActiveOrder, ActiveOrder, OrderId}
import coinffeine.model.network.PeerId
import coinffeine.model.properties.{MutableProperty, MutablePropertyMap}
import coinffeine.peer.api.CoinffeineNetwork

class MockCoinffeineNetwork extends CoinffeineNetwork {

  override val activePeers: MutableProperty[Int] = new MutableProperty(0)
  override val brokerId: MutableProperty[Option[PeerId]] = new MutableProperty(None)
  override val orders: MutablePropertyMap[OrderId, AnyCurrencyActiveOrder] = new MutablePropertyMap

  override def cancelOrder(orderId: OrderId): Unit = {
    orders.get(orderId).foreach(order => orders.set(orderId, order.cancel(DateTime.now())))
  }

  override def submitOrder[C <: FiatCurrency](order: ActiveOrder[C]): ActiveOrder[C] = {
    orders.set(order.id, order)
    order
  }
}
