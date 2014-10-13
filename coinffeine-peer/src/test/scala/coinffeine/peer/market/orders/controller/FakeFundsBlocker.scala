package coinffeine.peer.market.orders.controller

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market.RequiredFunds

class FakeFundsBlocker extends FundsBlocker {

  private var pending = Seq.empty[FundsBlocker.Listener]

  override def blockFunds(id: ExchangeId,
                          funds: RequiredFunds[_ <: FiatCurrency],
                          listener: FundsBlocker.Listener): Unit = {
    pending :+= listener
  }

  def successfullyBlockFunds(): Unit = {
    require(pending.nonEmpty, "No funds were requested")
    pending.head.onSuccess()
    pending = pending.tail
  }

  def failToBlockFunds(cause: Throwable = new Error("Injected error")): Unit = {
    require(pending.nonEmpty, "No funds were requested")
    pending.head.onFailure(cause)
    pending = pending.tail
  }
}
