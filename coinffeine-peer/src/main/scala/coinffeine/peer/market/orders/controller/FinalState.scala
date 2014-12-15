package coinffeine.peer.market.orders.controller

import coinffeine.model.currency.FiatCurrency
import coinffeine.protocol.messages.brokerage.OrderMatch

private[controller] class FinalState[C <: FiatCurrency] extends State[C] {

  override def shouldAcceptOrderMatch(ctx: Context, orderMatch: OrderMatch[C]) =
    MatchRejected("Order already finished")

  override def cancel(ctx: Context): Unit = {
    throw new UnsupportedOperationException("Already finished order")
  }
}
