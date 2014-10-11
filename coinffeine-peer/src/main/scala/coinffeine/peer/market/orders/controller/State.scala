package coinffeine.peer.market.orders.controller

import scala.util.Try

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.protocol.messages.brokerage.OrderMatch

/** Any of the possible OrderController states.
  *
  * Public methods represent events relevant to the states and the [[StateContext]] object passed
  * as their first argument provides the means to react to them to the state.
  */
private[controller] trait State[C <: FiatCurrency] {
  type Context = StateContext[C]

  /** Triggered when the state become current */
  def enter(ctx: Context): Unit = {}

  /** Funds were successfully blocked */
  def fundsBlocked(ctx: Context): Unit = {}
  /** Funds blocking failed */
  def cannotBlockFunds(ctx: Context, cause: Throwable): Unit = {}

  /** The order is published on the market */
  def becomeInMarket(ctx: Context): Unit = {}

  /** The order stop being published on the market */
  def becomeOffline(ctx: Context): Unit = {}

  /** An exchange just finished */
  def exchangeCompleted(ctx: Context, exchange: CompletedExchange[C]): Unit = {}

  /** An order match has been received. This request should be accepted or rejected using
    * [[StateContext.resolveOrderMatch()]] method.
    */
  def acceptOrderMatch(ctx: Context, orderMatch: OrderMatch[C]): Unit

  /** Triggered when the order should be cancelled */
  def cancel(ctx: Context, reason: String): Unit
}
