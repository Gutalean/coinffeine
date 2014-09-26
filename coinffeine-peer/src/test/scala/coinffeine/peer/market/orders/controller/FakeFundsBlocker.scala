package coinffeine.peer.market.orders.controller

import scala.util.{Failure, Success}

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange
import coinffeine.model.market.RequiredFunds

class FakeFundsBlocker extends FundsBlocker {

  private var pending = Seq.empty[FundsBlocker.Listener]

  override def blockFunds(funds: RequiredFunds[_ <: FiatCurrency],
                          listener: FundsBlocker.Listener): Unit = {
    pending :+= listener
  }

  def successfullyBlockFunds(blockedFunds: Exchange.BlockedFunds): Unit = {
    require(pending.nonEmpty, "No funds were requested")
    pending.head.onComplete(Success(blockedFunds))
    pending = pending.tail
  }

  def failToBlockFunds(cause: Throwable = new Error("Injected error")): Unit = {
    require(pending.nonEmpty, "No funds were requested")
    pending.head.onComplete(Failure(cause))
    pending = pending.tail
  }
}
