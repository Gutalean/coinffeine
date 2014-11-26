package coinffeine.headless.commands

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{Order, OrderId}
import coinffeine.peer.api.CoinffeineNetwork

class DummyCoinffeineNetwork extends CoinffeineNetwork {
  override def cancelOrder(order: OrderId, reason: String): Unit = {}
  override def submitOrder[C <: FiatCurrency](order: Order[C]): Order[C] = order
  override val brokerId = null
  override val activePeers = null
  override val orders = null
}
