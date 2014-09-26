package coinffeine.peer.market.orders.controller

import scala.util.Try

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange
import coinffeine.model.market.RequiredFunds

/** Encapsulates how funds for an exchange are blocked */
trait FundsBlocker {

  /** Block bitcoin funds and, if needed, fiat funds for an exchange.
    *
    * @param funds     What to block
    * @param listener  Who to notify of the operation result
    */
  def blockFunds(funds: RequiredFunds[_ <: FiatCurrency], listener: FundsBlocker.Listener): Unit
}

object FundsBlocker {
  trait Listener {
    def onComplete(maybeFunds: Try[Exchange.BlockedFunds]): Unit
  }
}
