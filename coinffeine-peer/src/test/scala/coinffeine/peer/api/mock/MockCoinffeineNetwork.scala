package coinffeine.peer.api.mock

import scala.concurrent.Future

import coinffeine.model.currency.FiatAmount
import coinffeine.model.exchange.AnyExchange
import coinffeine.model.market.{OrderBookEntry, OrderId}
import coinffeine.model.network.PeerId
import coinffeine.peer.api.CoinffeineNetwork
import coinffeine.peer.api.CoinffeineNetwork._

class MockCoinffeineNetwork(override val peerId: PeerId) extends CoinffeineNetwork {

  private var _orders: Set[OrderBookEntry[FiatAmount]] = Set.empty

  override def status: Status = ???

  override def exchanges: Set[AnyExchange] = ???

  override def disconnect(): Future[Disconnected.type] = ???

  override def orders: Set[OrderBookEntry[FiatAmount]] = _orders

  override def cancelOrder(orderId: OrderId): Unit = {
    _orders = _orders.filterNot(_.id == orderId)
  }

  override def submitOrder[F <: FiatAmount](order: OrderBookEntry[F]): OrderBookEntry[F] = {
    _orders += order
    order
  }

  override def connect(): Future[Connected.type] = ???
}
