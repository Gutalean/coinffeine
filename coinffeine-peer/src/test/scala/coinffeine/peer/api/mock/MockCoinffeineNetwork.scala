package coinffeine.peer.api.mock

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{Order, OrderId}
import coinffeine.peer.api.CoinffeineNetwork
import coinffeine.peer.api.CoinffeineNetwork._

class MockCoinffeineNetwork extends CoinffeineNetwork {

  private var _orders: Set[Order[c] forSome { type c <: FiatCurrency }] = Set.empty

  override def status: Status = ???

  override def orders = _orders

  override def cancelOrder(orderId: OrderId, reason: String): Unit = {
    _orders = _orders.filterNot(_.id == orderId)
  }

  override def submitOrder[C <: FiatCurrency](order: Order[C]): Order[C] = {
    _orders += order
    order
  }
}
