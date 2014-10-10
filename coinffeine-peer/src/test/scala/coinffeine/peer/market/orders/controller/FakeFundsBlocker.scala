package coinffeine.peer.market.orders.controller

import scala.util.{Failure, Success}

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.{Exchange, ExchangeId}
import coinffeine.model.market.RequiredFunds

class FakeFundsBlocker extends FundsBlocker {

  private case class PendingFundsBlock(listener: FundsBlocker.Listener) {

    def succeed(coinsId: ExchangeId): Unit = {
      listener.onComplete(Success(Exchange.BlockedFunds(coinsId)))
    }

    def fail(cause: Throwable): Unit = {
      listener.onComplete(Failure(cause))
    }
  }

  private var pending = Seq.empty[PendingFundsBlock]

  override def blockFunds(id: ExchangeId,
                          funds: RequiredFunds[_ <: FiatCurrency],
                          listener: FundsBlocker.Listener): Unit = {
    pending :+= PendingFundsBlock(listener)
  }

  def successfullyBlockFunds(coinsId: ExchangeId): Unit = {
    require(pending.nonEmpty, "No funds were requested")
    pending.head.succeed(coinsId)
    pending = pending.tail
  }

  def failToBlockFunds(cause: Throwable = new Error("Injected error")): Unit = {
    require(pending.nonEmpty, "No funds were requested")
    pending.head.fail(cause)
    pending = pending.tail
  }
}
