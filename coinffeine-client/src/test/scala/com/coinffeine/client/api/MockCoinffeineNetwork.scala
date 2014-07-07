package com.coinffeine.client.api

import scala.concurrent.Future

import com.coinffeine.client.api.CoinffeineNetwork._
import com.coinffeine.common.Order

class MockCoinffeineNetwork extends CoinffeineNetwork {

  private var _orders: Set[Order] = Set.empty

  override def status: Status = ???

  override def exchanges: Set[Exchange] = ???

  override def onExchangeChanged(listener: ExchangeListener): Unit = ???

  override def disconnect(): Future[Disconnected.type] = ???

  override def orders: Set[Order] = _orders

  override def cancelOrder(order: Order): Unit = {
    _orders -= order
  }

  override def submitOrder(order: Order): Order = {
    _orders += order
    order
  }

  override def connect(): Future[Connected.type] = ???
}
