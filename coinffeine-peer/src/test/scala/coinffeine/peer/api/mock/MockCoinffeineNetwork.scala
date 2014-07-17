package coinffeine.peer.api.mock

import scala.concurrent.Future

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange
import coinffeine.model.market.{Order, OrderId}
import coinffeine.model.network.PeerId
import coinffeine.peer.api.CoinffeineNetwork
import coinffeine.peer.api.CoinffeineNetwork._

class MockCoinffeineNetwork(override val peerId: PeerId) extends CoinffeineNetwork {

  private var _orders: Set[Order[FiatCurrency]] = Set.empty

  override def status: Status = ???

  override def exchanges: Set[Exchange[FiatCurrency]] = ???

  override def disconnect(): Future[Disconnected.type] = ???

  override def orders: Set[Order[FiatCurrency]] = _orders

  override def cancelOrder(orderId: OrderId): Unit = {
    _orders = _orders.filterNot(_.id == orderId)
  }

  override def submitOrder[C <: FiatCurrency](order: Order[C]): Order[C] = {
    _orders += order
    order
  }

  override def connect(): Future[Connected.type] = ???
}
