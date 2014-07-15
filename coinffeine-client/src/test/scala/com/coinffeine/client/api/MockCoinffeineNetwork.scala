package com.coinffeine.client.api

import scala.concurrent.Future

import coinffeine.model.currency.FiatAmount
import coinffeine.model.market.{OrderBookEntry, OrderId}
import com.coinffeine.client.api.CoinffeineNetwork._

class MockCoinffeineNetwork extends CoinffeineNetwork {

  private var _orders: Set[OrderBookEntry[FiatAmount]] = Set.empty

  override def status: Status = ???

  override def exchanges: Set[Exchange] = ???

  override def onExchangeChanged(listener: ExchangeListener): Unit = ???

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
