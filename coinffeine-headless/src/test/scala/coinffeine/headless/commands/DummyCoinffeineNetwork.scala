package coinffeine.headless.commands

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{AnyCurrencyOrder, Order, OrderId}
import coinffeine.model.network.PeerId
import coinffeine.model.properties.{Property, PropertyMap}
import coinffeine.peer.api.CoinffeineNetwork

class DummyCoinffeineNetwork extends CoinffeineNetwork {
  override def cancelOrder(order: OrderId): Unit = {}
  override def submitOrder[C <: FiatCurrency](order: Order[C]): Order[C] = order
  override val activePeers: Property[Int] = null
  override val brokerId: Property[Option[PeerId]] = null
  override val orders: PropertyMap[OrderId, AnyCurrencyOrder] = null
}
