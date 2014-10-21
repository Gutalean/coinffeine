package coinffeine.peer.market.orders.controller

import scala.util.{Failure, Try}

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market.RequiredFunds

/** Encapsulates how funds for an exchange are blocked */
trait FundsBlocker {

  /** Block bitcoin funds and, if needed, fiat funds for an exchange.
    *
    * @param id        Identifies what exchange the funds are for
    * @param funds     What to block
    * @param listener  Who to notify of the operation result
    */
  def blockFunds(id: ExchangeId,
                 funds: RequiredFunds[_ <: FiatCurrency],
                 listener: FundsBlocker.Listener): Unit
}

object FundsBlocker {
  trait Listener {
    def fundsBlocked(): Unit
    def cannotBlockFunds(cause: Throwable): Unit

    final def onComplete(result: Try[Unit]): Unit = result match {
      case Failure(cause) => cannotBlockFunds(cause)
      case _ => fundsBlocked()
    }
  }
}
