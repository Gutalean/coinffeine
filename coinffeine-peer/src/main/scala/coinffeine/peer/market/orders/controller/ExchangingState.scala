package coinffeine.peer.market.orders.controller

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.model.market.InProgressOrder
import coinffeine.protocol.messages.brokerage.OrderMatch

private[controller] class ExchangingState[C <: FiatCurrency](exchangeInProgress: ExchangeId)
  extends State[C] {

  override def enter(ctx: Context): Unit = {
    ctx.updateOrderStatus(InProgressOrder)
  }

  override def fundsBecomeUnavailable(ctx: Context): Unit = {
    // TODO: case not yet implemented
    throw new UnsupportedOperationException("Case not yet considered")
  }

  override def exchangeCompleted(ctx: Context, exchange: CompletedExchange[C]): Unit = {
    if (exchange.state.isSuccess) {
      ctx.transitionTo(new FinalState(FinalState.OrderCompletion))
    } else {
      throw new NotImplementedError(s"Don't know what to do with $exchange")
    }
  }

  override def acceptOrderMatch(ctx: Context, orderMatch: OrderMatch) =
    if (exchangeInProgress == orderMatch.exchangeId)
      MatchAlreadyAccepted(ctx.order.exchanges(exchangeInProgress))
    else MatchRejected("Exchange already in progress")

  override def cancel(ctx: Context, reason: String): Unit = {
    // TODO: is this what we wanna do?
    ctx.transitionTo(new FinalState(FinalState.OrderCancellation(reason)))
  }
}
