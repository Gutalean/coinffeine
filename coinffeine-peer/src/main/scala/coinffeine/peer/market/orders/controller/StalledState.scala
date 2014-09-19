package coinffeine.peer.market.orders.controller

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.StalledOrder
import coinffeine.protocol.messages.brokerage.OrderMatch

private[controller] case class StalledState[C <: FiatCurrency]() extends State[C] {

  override def enter(ctx: Context): Unit = {
    ctx.updateOrderStatus(StalledOrder(stallReason(ctx)))
  }

  override def fundsBecomeAvailable(ctx: Context): Unit = {
    ctx.transitionTo(new WaitingForMatchesState)
  }

  override def acceptOrderMatch(ctx: Context, ignored: OrderMatch[C]) =
    MatchRejected(stallReason(ctx))

  override def cancel(ctx: Context, reason: String): Unit = {
    ctx.transitionTo(new FinalState(FinalState.OrderCancellation(reason)))
  }

  private def stallReason(ctx: Context): String =
    if (ctx.funds.areBlocked) "No funds available"
    else "Blocking funds"
}
