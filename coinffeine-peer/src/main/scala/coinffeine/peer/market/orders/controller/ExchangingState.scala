package coinffeine.peer.market.orders.controller

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.model.market.InProgressOrder
import coinffeine.protocol.messages.brokerage.OrderMatch

private[controller] class ExchangingState[C <: FiatCurrency](exchangeInProgress: ExchangeId)
  extends State[C] {

  override def enter(ctx: Context): Unit = {
    ctx.keepOffMarket()
    ctx.updateOrderStatus(InProgressOrder)
  }

  override def exchangeCompleted(ctx: Context, exchange: CompletedExchange[C]): Unit = {
    ctx.transitionTo(
      if (ctx.order.amounts.pending.isPositive) new WaitingForMatchesState()
      else new FinalState(FinalState.OrderCompletion)
    )
  }

  override def shouldAcceptOrderMatch(ctx: Context, orderMatch: OrderMatch[C]): MatchResult[C] =
    if (exchangeInProgress == orderMatch.exchangeId)
      MatchAlreadyAccepted[C](ctx.order.exchanges(exchangeInProgress))
    else MatchRejected[C]("Exchange already in progress")

  override def cancel(ctx: Context): Unit = {
    // TODO: is this what we wanna do?
    ctx.transitionTo(new FinalState(FinalState.OrderCancellation))
  }
}
