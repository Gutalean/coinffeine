package coinffeine.peer.market.orders.controller

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

  /** An exchange just finished */
  def exchangeCompleted(ctx: Context, exchange: CompletedExchange[C]): Unit = {}

  /** Whether to accept or not an order match */
  def shouldAcceptOrderMatch(ctx: Context, orderMatch: OrderMatch[C]): MatchResult[C]

  /** When an order match has been accepted */
  def acceptedOrderMatch(ctx: Context, orderMatch: OrderMatch[C]): Unit = {}

  /** Triggered when the order should be cancelled */
  def cancel(ctx: Context): Unit
}
