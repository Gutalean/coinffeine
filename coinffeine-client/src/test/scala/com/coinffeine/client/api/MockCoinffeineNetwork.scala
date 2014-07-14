package com.coinffeine.client.api

import scala.concurrent.Future

import com.coinffeine.client.api.CoinffeineNetwork._
import com.coinffeine.common.{FiatAmount, Order, OrderId}

class MockCoinffeineNetwork extends CoinffeineNetwork {

  private var _orders: Set[Order[FiatAmount]] = Set.empty

  override def status: Status = ???

  override def exchanges: Set[Exchange] = ???

  override def onExchangeChanged(listener: ExchangeListener): Unit = ???

  override def disconnect(): Future[Disconnected.type] = ???

  override def orders: Set[Order[FiatAmount]] = _orders

  override def cancelOrder(orderId: OrderId): Unit = {
    _orders = _orders.filterNot(_.id == orderId)
  }

  override def submitOrder[F <: FiatAmount](order: Order[F]): Order[F] = {
    _orders += order
    order
  }

  override def connect(): Future[Connected.type] = ???
}
