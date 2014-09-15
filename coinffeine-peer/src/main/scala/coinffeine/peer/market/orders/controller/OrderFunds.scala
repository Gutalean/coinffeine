package coinffeine.peer.market.orders.controller

import coinffeine.model.exchange.Exchange

trait OrderFunds {

  protected var listeners = Seq.empty[OrderFunds.Listener]

  def addListener(listener: OrderFunds.Listener): Unit = {
    listeners :+= listener
  }

  /** Whether the funds have been blocked */
  def areBlocked: Boolean

  /** Whether the funds are available for use */
  def areAvailable: Boolean

  /** Get the blocked funds if they were already blocked */
  @throws[NoSuchElementException]
  def get: Exchange.BlockedFunds

  /** Release remaining funds if any.
    *
    * This should be the last method call on [[OrderFunds]].
    */
  def release(): Unit
}

object OrderFunds {
  trait Listener {
    def onFundsAvailable(funds: OrderFunds): Unit
    def onFundsUnavailable(funds: OrderFunds): Unit
  }
}
