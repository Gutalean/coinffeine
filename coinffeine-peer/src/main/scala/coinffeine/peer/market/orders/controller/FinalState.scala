package coinffeine.peer.market.orders.controller

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{CancelledOrder, CompletedOrder}
import coinffeine.protocol.messages.brokerage.OrderMatch

private[controller] case class FinalState[C <: FiatCurrency](cause: FinalState.Cause) extends State[C] {
  import FinalState._

  override def enter(ctx: Context): Unit = {
    ctx.updateOrderStatus(cause match {
      case OrderCompletion => CompletedOrder
      case OrderCancellation(reason) => CancelledOrder(reason)
    })
  }

  override def acceptOrderMatch(ctx: Context, ignored: OrderMatch[C]) =
    MatchRejected("Order already finished")

  override def cancel(ctx: Context, reason: String): Unit = {
    throw new UnsupportedOperationException("Already finished order")
  }
}

object FinalState {
  sealed trait Cause
  case object OrderCompletion extends Cause
  case class OrderCancellation(reason: String) extends Cause
}
