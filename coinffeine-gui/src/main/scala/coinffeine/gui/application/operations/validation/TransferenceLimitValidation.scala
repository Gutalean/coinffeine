package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList

import coinffeine.common.properties.Property
import coinffeine.model.currency._
import coinffeine.model.market.Spread
import coinffeine.model.order.{Ask, Bid, OrderRequest}
import coinffeine.model.util.Cached
import coinffeine.peer.amounts.AmountsCalculator

private class TransferenceLimitValidation(
    amountsCalculator: AmountsCalculator,
    remainingLimits: Property[Cached[FiatAmounts]]) extends OrderValidation {

  override def apply(order: OrderRequest, spread: Spread): OrderValidation.Result =
    order.orderType match {
      case Bid =>
        val cachedLimits = remainingLimits.get
        if (!cachedLimits.status.isFresh) cannotCheckLimits(order.price.currency)
        else checkLimit(order, spread, cachedLimits.cached.get(order.price.currency))
      case Ask => OrderValidation.OK
    }

  private def checkLimit(
      order: OrderRequest,
      spread: Spread,
      maybeLimit: Option[FiatAmount]): OrderValidation.Result =
    (for {
      requiredAmounts <- amountsCalculator.estimateAmountsFor(order, spread)
      requiredAmount = requiredAmounts.fiatRequired.buyer
      limit <- maybeLimit
      if requiredAmount > limit
    } yield OrderValidation.Warning(NonEmptyList(
      s"You need to transfer $requiredAmount but your remaining limit of $limit is " +
          "not enough.\nYou may proceed but your order can fail when you hit your " +
          "transference limit."
    ))).getOrElse(OrderValidation.OK)

  private def cannotCheckLimits(currency: FiatCurrency) = OrderValidation.Warning(NonEmptyList(
    s"It is not possible to check your $currency limit.\n" +
      "You may proceed but your order can fail if you reach your actual transference limit."
  ))
}
