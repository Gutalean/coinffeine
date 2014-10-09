package coinffeine.peer.market.orders.controller

import scala.util.{Failure, Success}

import coinffeine.model.bitcoin.BlockedCoinsId
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.{Exchange, ExchangeId}
import coinffeine.model.market.RequiredFunds

class FakeFundsBlocker extends FundsBlocker {

  private case class PendingFundsBlock(id: ExchangeId, listener: FundsBlocker.Listener) {

    def succeed(coinsId: BlockedCoinsId): Unit = {
      listener.onComplete(Success(Exchange.BlockedFunds(Some(id), coinsId)))
    }

    def fail(cause: Throwable): Unit = {
      listener.onComplete(Failure(cause))
    }
  }

  private var pending = Seq.empty[PendingFundsBlock]

  override def blockFunds(id: ExchangeId,
                          funds: RequiredFunds[_ <: FiatCurrency],
                          listener: FundsBlocker.Listener): Unit = {
    pending :+= PendingFundsBlock(id, listener)
  }

  def successfullyBlockFunds(coinsId: BlockedCoinsId): Unit = {
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
