package coinffeine.peer.market.orders.controller

import scala.util.{Failure, Success, Try}

import org.slf4j.LoggerFactory

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange.{Amounts, BlockedFunds}
import coinffeine.model.exchange.{Exchange, Role}
import coinffeine.model.market._
import coinffeine.protocol.messages.brokerage.OrderMatch

private[controller] class WaitingForMatchesState[C <: FiatCurrency] extends State[C] {

  private var matchWaitingForFunds: Option[OrderMatch[C]] = None

  override def enter(ctx: Context): Unit = {
    ctx.updateOrderStatus(OfflineOrder)
    ctx.keepInMarket()
  }

  override def becomeInMarket(ctx: Context): Unit = {
    ctx.updateOrderStatus(InMarketOrder)
  }

  override def becomeOffline(ctx: Context): Unit = {
    ctx.updateOrderStatus(OfflineOrder)
  }

  override def fundsRequestResult(ctx: Context, blockedFunds: Try[BlockedFunds]): Unit = {
    (matchWaitingForFunds, blockedFunds) match {
      case (Some(orderMatch), Success(funds)) => startExchange(ctx, orderMatch, funds)

      case (Some(orderMatch), Failure(cause)) =>
        WaitingForMatchesState.Log.error(s"Cannot block funds for $orderMatch", cause)
        ctx.resolveOrderMatch(orderMatch, MatchRejected("Cannot block funds"))

      case _ => WaitingForMatchesState.Log.warn("Unexpected blocked funds result {}", blockedFunds)
    }
    matchWaitingForFunds = None
  }

  override def acceptOrderMatch(ctx: Context, orderMatch: OrderMatch[C]): Unit = {
    if (matchWaitingForFunds.nonEmpty) {
      ctx.resolveOrderMatch(orderMatch, MatchRejected("Accepting other match"))
      return
    }
    if (hasInvalidAmount(ctx.order, orderMatch)) {
      ctx.resolveOrderMatch(orderMatch, MatchRejected("Invalid amount"))
      return
    }
    if (hasInvalidPrice(ctx.order, orderMatch)) {
      ctx.resolveOrderMatch(orderMatch, MatchRejected("Invalid price"))
      return
    }
    val amounts = ctx.calculator.exchangeAmountsFor(orderMatch)
    if (hasConsistentAmounts(orderMatch, amounts)) {
      ctx.resolveOrderMatch(orderMatch, MatchRejected("Match with inconsistent amounts"))
      return
    }
    val requiredFunds = RequiredFunds(
      ctx.order.role.select(amounts.bitcoinRequired),
      ctx.order.role.select(amounts.fiatRequired)
    )
    matchWaitingForFunds = Some(orderMatch)
    ctx.blockFunds(requiredFunds)
  }

  private def hasConsistentAmounts(orderMatch: OrderMatch[C], amounts: Amounts[C]): Boolean = {
    orderMatch.bitcoinAmount.buyer != amounts.netBitcoinExchanged ||
      orderMatch.fiatAmount.seller != amounts.netFiatExchanged
  }

  private def startExchange(ctx: Context,
                            orderMatch: OrderMatch[C],
                            blockedFunds: BlockedFunds): Unit = {
    val exchange = Exchange.notStarted(
      id = orderMatch.exchangeId,
      Role.fromOrderType(ctx.order.orderType),
      counterpartId = orderMatch.counterpart,
      ctx.calculator.exchangeAmountsFor(orderMatch),
      Exchange.Parameters(orderMatch.lockTime, ctx.network),
      blockedFunds
    )
    ctx.resolveOrderMatch(orderMatch, MatchAccepted(exchange))
    ctx.keepOffMarket()
    ctx.transitionTo(new ExchangingState(exchange.id))
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

object WaitingForMatchesState {
  private val Log = LoggerFactory.getLogger(classOf[WaitingForMatchesState[_]])
}
