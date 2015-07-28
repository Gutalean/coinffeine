package coinffeine.gui.application.operations.validation

import scalaz.Validation.FlatMap._
import scalaz.syntax.std.option._
import scalaz.syntax.validation._
import scalaz.{NonEmptyList, Validation}

import coinffeine.common.properties.Property
import coinffeine.gui.application.operations.validation.OrderValidation.Problem
import coinffeine.model.currency._
import coinffeine.model.currency.balance.FiatBalance
import coinffeine.model.market.Spread
import coinffeine.model.order.{Ask, Bid, OrderRequest}
import coinffeine.model.util.Cached
import coinffeine.peer.amounts.AmountsCalculator

private class TransferenceLimitValidation(
    amountsCalculator: AmountsCalculator,
    remainingLimits: Property[Cached[FiatBalance]]) extends OrderValidation {

  override def apply(order: OrderRequest, spread: Spread): OrderValidation.Result =
    order.orderType match {
      case Bid => checkBidOrder(order, spread)
      case Ask => OrderValidation.Ok
    }

  private def checkBidOrder(order: OrderRequest, spread: Spread): Validation[Problem, Unit] =
    for {
      remainingLimit <- requireFreshLimit(order.price.currency)
      requiredAmount <- requireAmountCanBeEstimated(order, spread)
      _ <- requireLimitIsBigEnough(requiredAmount, remainingLimit)
    } yield {}

  private def requireFreshLimit(currency: FiatCurrency): Validation[Problem, Option[FiatAmount]] = {
    val cachedLimits = remainingLimits.get
    if (!cachedLimits.status.isFresh) cannotCheckLimits(currency)
    else cachedLimits.cached.remainingLimits.get(currency).success
  }

  private def requireAmountCanBeEstimated(
      order: OrderRequest, spread: Spread): Validation[Problem, FiatAmount] =
    amountsCalculator.estimateAmountsFor(order, spread)
      .map(_.fiatRequired.buyer)
      .toSuccess(OrderValidation.Warning(NonEmptyList(
        s"Cannot estimate the ${order.price.currency} required for this order. " +
          s"You might need to transfer more money than your limits allow.")))

  private def requireLimitIsBigEnough(
      requiredAmount: FiatAmount, remainingLimit: Option[FiatAmount]): OrderValidation.Result =
    if (remainingLimit.exists(_ < requiredAmount))
      limitIsNotEnough(requiredAmount, remainingLimit.get)
    else OrderValidation.Ok

  private def limitIsNotEnough(requiredAmount: FiatAmount, remainingLimit: FiatAmount) =
    OrderValidation.error(
      "This order will exceed your payment processor monthly transference limits.")

  private def cannotCheckLimits(currency: FiatCurrency) = OrderValidation.warning(
    s"It is not possible to check your $currency limit.\n" +
      "You may proceed but your order can fail if you reach your actual transference limit."
  )
}
