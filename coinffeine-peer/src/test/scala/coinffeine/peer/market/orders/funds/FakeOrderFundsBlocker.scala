package coinffeine.peer.market.orders.funds

import scala.util.{Failure, Success, Try}

import akka.actor.Actor.Receive

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market.RequiredFunds
import coinffeine.peer.market.orders.OrderActor
import coinffeine.peer.market.orders.controller.FundsBlocker.Listener

class FakeOrderFundsBlocker extends OrderActor.OrderFundsBlocker {

  private var result: Try[Unit] = _

  def givenSuccessfulFundsBlocking(): Unit = synchronized {
    result = Success {}
  }

  def givenFailingFundsBlocking(message: String): Unit = synchronized {
    result = Failure(new Error(message))
  }

  override def blockFunds(id: ExchangeId,
                          funds: RequiredFunds[_ <: FiatCurrency],
                          listener: Listener): Unit =
    synchronized { listener.onComplete(result) }

  override def blockingFunds: Receive = Map.empty
}
