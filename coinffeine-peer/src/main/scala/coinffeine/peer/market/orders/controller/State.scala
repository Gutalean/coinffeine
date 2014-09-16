package coinffeine.peer.market.orders.controller

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.protocol.messages.brokerage.OrderMatch

private[controller] trait State[C <: FiatCurrency] {
  type Context = StateContext[C]
  def enter(ctx: Context): Unit = {}
  def fundsBecomeAvailable(ctx: Context): Unit = {}
  def fundsBecomeUnavailable(ctx: Context): Unit = {}
  def becomeInMarket(ctx: Context): Unit = {}
  def becomeOffline(ctx: Context): Unit = {}
  def exchangeCompleted(ctx: Context, exchange: CompletedExchange[C]): Unit = {}
  def acceptOrderMatch(ctx: Context, orderMatch: OrderMatch): MatchResult[C]
  def cancel(ctx: Context, reason: String): Unit
}
