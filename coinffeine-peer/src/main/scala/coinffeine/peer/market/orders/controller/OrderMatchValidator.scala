package coinffeine.peer.market.orders.controller

import scalaz.Validation
import scalaz.Validation.FlatMap._
import scalaz.syntax.validation._

import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.exchange.Role
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.model.order._
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.protocol.messages.brokerage.OrderMatch

private class OrderMatchValidator(peerId: PeerId, calculator: AmountsCalculator) {

  def shouldAcceptOrderMatch[C <: FiatCurrency](order: ActiveOrder[C],
                                                orderMatch: OrderMatch[C],
                                                alreadyBlocking: Bitcoin.Amount): MatchResult[C] = {

    type MatchValidation = Validation[MatchResult[C], Unit]

    lazy val amounts = calculator.exchangeAmountsFor(orderMatch)

    def requireNoSelfCrossing: MatchValidation =
      require(orderMatch.counterpart != peerId, MatchRejected("Self-cross"))

    def requireUnfinishedOrder: MatchValidation = {
      val isFinished = Seq(OrderStatus.Cancelled, OrderStatus.Completed).contains(order.status)
      require(!isFinished, MatchRejected("Order already finished"))
    }

    def requireNotAcceptedPreviously: MatchValidation =
      require(!order.exchanges.contains(orderMatch.exchangeId),
        MatchAlreadyAccepted(order.exchanges(orderMatch.exchangeId)))

    def requireValidAmount: MatchValidation = {
      val role = Role.fromOrderType(order.orderType)
      val remainingAmount = order.amounts.pending - alreadyBlocking
      val matchedAmount = role.select(orderMatch.bitcoinAmount)
      require(remainingAmount >= matchedAmount,
        MatchRejected(s"Invalid amount: $remainingAmount remaining, $matchedAmount offered"))
    }

    def requireValidPrice: MatchValidation = order.price match {
      case LimitPrice(price) => requireValidLimitPrice(price)
      case MarketPrice(_) => ().success
    }

    def requireValidLimitPrice(price: Price[C]): MatchValidation = {
      val role = Role.fromOrderType(order.orderType)
      val limitFiat = price.of(role.select(orderMatch.bitcoinAmount))
      val actualFiat = role.select(orderMatch.fiatAmount)
      val isOffLimit = order.orderType match {
        case Bid => actualFiat > limitFiat
        case Ask => actualFiat < limitFiat
      }
      require(!isOffLimit, MatchRejected(s"Invalid price: offered $actualFiat, limit $limitFiat"))
    }

    def requireConsistentAmounts: MatchValidation = for {
      _ <- require(orderMatch.bitcoinAmount.buyer == amounts.netBitcoinExchanged, MatchRejected(
        s"Match with inconsistent amounts: bitcoin amounts ${orderMatch.bitcoinAmount}"))
      _ <- require(orderMatch.fiatAmount.seller == amounts.netFiatExchanged, MatchRejected(
        s"Match with inconsistent amounts: fiat amounts ${orderMatch.fiatAmount}"))
    } yield {}

    def require(predicate: Boolean, result: => MatchResult[C]): MatchValidation =
      if (predicate) ().success else result.failure

    (for {
      _ <- requireNoSelfCrossing
      _ <- requireUnfinishedOrder
      _ <- requireNotAcceptedPreviously
      _ <- requireValidAmount
      _ <- requireValidPrice
      _ <- requireConsistentAmounts
    } yield MatchAccepted(RequiredFunds(
      order.role.select(amounts.bitcoinRequired),
      order.role.select(amounts.fiatRequired)
    ))).fold(identity, identity)
  }
}
