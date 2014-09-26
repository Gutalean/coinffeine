package coinffeine.peer.market.orders.funds

import scala.util.{Failure, Success, Try}

import akka.actor.Actor.Receive

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange
import coinffeine.model.market.RequiredFunds
import coinffeine.peer.market.orders.OrderActor
import coinffeine.peer.market.orders.controller.FundsBlocker.Listener

class FakeOrderFundsBlocker extends OrderActor.OrderFundsBlocker {

  private var result: Try[Exchange.BlockedFunds] = _

  def givenSuccessfulFundsBlocking(blockedFunds: Exchange.BlockedFunds): Unit = synchronized {
    result = Success(blockedFunds)
  }

  def givenFailingFundsBlocking(message: String): Unit = synchronized {
    result = Failure(new Error(message))
  }

  override def blockFunds(funds: RequiredFunds[_ <: FiatCurrency], listener: Listener): Unit =
    synchronized { listener.onComplete(result) }

  override def blockingFunds: Receive = Map.empty
}
