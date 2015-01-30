package coinffeine.peer.market.orders.controller

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Role
import coinffeine.model.market._
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.protocol.messages.brokerage.OrderMatch

private class OrderMatchValidator(calculator: AmountsCalculator) {

  def shouldAcceptOrderMatch[C <: FiatCurrency](order: Order[C],
                                                orderMatch: OrderMatch[C]): MatchResult[C] = {

    val limitPrice = order.price.toOption.getOrElse(
      throw new IllegalArgumentException("Unsupported market price orders"))
    lazy val amounts = calculator.exchangeAmountsFor(orderMatch)

    def isFinished: Boolean = Seq(CancelledOrder, CompletedOrder).contains(order.status)

    def hasBeenAlreadyAccepted: Boolean = order.exchanges.contains(orderMatch.exchangeId)

    def otherExchangeInProgress: Boolean = order.status == InProgressOrder

    def hasInvalidAmount: Boolean = {
      val role = Role.fromOrderType(order.orderType)
      order.amounts.pending.value < role.select(orderMatch.bitcoinAmount).value
    }

    def hasInconsistentAmounts: Boolean =
      orderMatch.bitcoinAmount.buyer != amounts.netBitcoinExchanged ||
        orderMatch.fiatAmount.seller != amounts.netFiatExchanged

    def hasInvalidPrice: Boolean = {
      val role = Role.fromOrderType(order.orderType)
      val matchPrice = Price.whenExchanging(
        role.select(orderMatch.bitcoinAmount), role.select(orderMatch.fiatAmount))
      order.orderType match {
        case Bid => limitPrice.underbids(matchPrice)
        case Ask => matchPrice.underbids(limitPrice)
      }
    }

    if (isFinished) MatchRejected("Order already finished")
    else if (hasBeenAlreadyAccepted) MatchAlreadyAccepted[C](order.exchanges(orderMatch.exchangeId))
    else if (otherExchangeInProgress) MatchRejected[C]("Exchange already in progress")
    else if (hasInvalidAmount) MatchRejected("Invalid amount")
    else if (hasInvalidPrice) MatchRejected("Invalid price")
    else if (hasInconsistentAmounts) MatchRejected("Match with inconsistent amounts")
    else MatchAccepted(RequiredFunds(
      order.role.select(amounts.bitcoinRequired),
      order.role.select(amounts.fiatRequired)
    ))
  }
}
