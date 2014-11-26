package coinffeine.peer.market.orders.controller

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange.Amounts
import coinffeine.model.exchange.Role
import coinffeine.model.market._
import coinffeine.protocol.messages.brokerage.OrderMatch

private[controller] class WaitingForMatchesState[C <: FiatCurrency] extends State[C] {

  override def enter(ctx: Context): Unit = {
    ctx.updateOrderStatus(OfflineOrder)
    ctx.keepInMarket()
  }

  override def shouldAcceptOrderMatch(ctx: Context, orderMatch: OrderMatch[C]) = {
    if (hasInvalidAmount(ctx.order, orderMatch)) MatchRejected("Invalid amount")
    else if (hasInvalidPrice(ctx.order, orderMatch)) MatchRejected("Invalid price")
    else {
      val amounts = ctx.calculator.exchangeAmountsFor(orderMatch)
      if (hasInconsistentAmounts(orderMatch, amounts)) MatchRejected("Match with inconsistent amounts")
      else MatchAccepted(RequiredFunds(
        ctx.order.role.select(amounts.bitcoinRequired),
        ctx.order.role.select(amounts.fiatRequired)
      ))
    }
  }

  override def acceptedOrderMatch(ctx: Context, orderMatch: OrderMatch[C]): Unit = {
    ctx.transitionTo(new ExchangingState(orderMatch.exchangeId))
  }

  private def hasInconsistentAmounts(orderMatch: OrderMatch[C], amounts: Amounts[C]): Boolean = {
    orderMatch.bitcoinAmount.buyer != amounts.netBitcoinExchanged ||
      orderMatch.fiatAmount.seller != amounts.netFiatExchanged
  }

  override def cancel(ctx: Context): Unit = {
    ctx.keepOffMarket()
    ctx.transitionTo(new FinalState(FinalState.OrderCancellation))
  }

  private def hasInvalidPrice(order: Order[C], orderMatch: OrderMatch[C]): Boolean = {
    val role = Role.fromOrderType(order.orderType)
    val matchPrice = Price.whenExchanging(
      role.select(orderMatch.bitcoinAmount), role.select(orderMatch.fiatAmount))
    order.orderType match {
      case Bid => order.price.underbids(matchPrice)
      case Ask => matchPrice.underbids(order.price)
    }
  }

  private def hasInvalidAmount(order: Order[C], orderMatch: OrderMatch[C]): Boolean = {
    val role = Role.fromOrderType(order.orderType)
    order.amounts.pending.value < role.select(orderMatch.bitcoinAmount).value
  }
}
