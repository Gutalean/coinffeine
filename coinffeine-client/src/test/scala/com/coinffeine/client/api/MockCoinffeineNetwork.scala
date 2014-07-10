package com.coinffeine.client.api

import scala.concurrent.Future

import com.coinffeine.client.api.CoinffeineNetwork._
import com.coinffeine.common.{FiatAmount, Order}

class MockCoinffeineNetwork extends CoinffeineNetwork {

  private var _orders: Set[Order[FiatAmount]] = Set.empty

  override def status: Status = ???

  override def exchanges: Set[Exchange] = ???

  override def onExchangeChanged(listener: ExchangeListener): Unit = ???

  override def disconnect(): Future[Disconnected.type] = ???

  override def orders: Set[Order[FiatAmount]] = _orders

  override def cancelOrder(order: Order[FiatAmount]): Unit = {
    _orders -= order
  }

  override def submitOrder[F <: FiatAmount](order: Order[F]): Order[F] = {
    _orders += order
    order
  }

  override def connect(): Future[Connected.type] = ???
}
