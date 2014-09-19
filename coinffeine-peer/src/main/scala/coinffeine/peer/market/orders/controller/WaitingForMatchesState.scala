package coinffeine.peer.market.orders.controller

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.{Exchange, Role}
import coinffeine.model.market._
import coinffeine.protocol.messages.brokerage.OrderMatch

private[controller] class WaitingForMatchesState[C <: FiatCurrency]() extends State[C] {

  override def enter(ctx: Context): Unit = {
    ctx.updateOrderStatus(OfflineOrder)
    ctx.keepInMarket()
  }

  override def fundsBecomeUnavailable(ctx: Context): Unit = {
    ctx.keepOffMarket()
    ctx.transitionTo(new StalledState)
  }

  override def becomeInMarket(ctx: Context): Unit = {
    ctx.updateOrderStatus(InMarketOrder)
  }

  override def becomeOffline(ctx: Context): Unit = {
    ctx.updateOrderStatus(OfflineOrder)
  }

  override def acceptOrderMatch(ctx: Context, orderMatch: OrderMatch[C]) = {
    if (hasInvalidPrice(ctx.order, orderMatch)) MatchRejected("Invalid price")
    else if (hasInvalidAmount(ctx.order, orderMatch)) MatchRejected("Invalid amount")
    else {
      val exchange = Exchange.notStarted(
        id = orderMatch.exchangeId,
        Role.fromOrderType(ctx.order.orderType),
        counterpartId = orderMatch.counterpart,
        ctx.calculator.exchangeAmountsFor(orderMatch),
        parameters = Exchange.Parameters(orderMatch.lockTime, ctx.network),
        ctx.funds.get
      )
      ctx.startExchange(exchange)
      ctx.keepOffMarket()
      ctx.transitionTo(new ExchangingState(exchange.id))
      MatchAccepted(exchange)
    }
  }

  override def cancel(ctx: Context, reason: String): Unit = {
    ctx.keepOffMarket()
    ctx.transitionTo(new FinalState(FinalState.OrderCancellation(reason)))
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
