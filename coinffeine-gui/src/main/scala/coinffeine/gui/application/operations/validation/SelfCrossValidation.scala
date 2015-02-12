package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList

import coinffeine.gui.application.operations.validation.OrderValidation.Result
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market._
import coinffeine.model.properties.PropertyMap

private class SelfCrossValidation(orders: PropertyMap[OrderId, AnyCurrencyOrder])
  extends OrderValidation {

  override def apply[C <: FiatCurrency](newOrder: Order[C]) = {
    newOrder.price match {
      case LimitPrice(limit) => validateLimitOrder(newOrder, limit)
      case MarketPrice(_) => OrderValidation.OK
    }
  }

  private def validateLimitOrder[C <: FiatCurrency](newOrder: Order[C], limit: Price[C]): Result = {
    val priceCrosses = newOrder.orderType match {
      case Bid => limit.outbidsOrMatches _
      case Ask => limit.underbidsOrMatches _
    }
    val crossedPrices = for {
      candidateOrder <- orders.values if suitableForSelfCross(newOrder, candidateOrder)
      crossPrice = candidateOrder.price.toOption.get.asInstanceOf[Price[C]]
      if priceCrosses(crossPrice)
    } yield crossPrice
    crossedPrices.headOption.fold[Result](OrderValidation.OK)(selfCross)
  }

  private def suitableForSelfCross(newOrder: Order[_ <: FiatCurrency],
                                   candidate: AnyCurrencyOrder): Boolean =
    candidate.price.isLimited && candidate.status.isActive &&
      candidate.price.currency == newOrder.price.currency &&
      candidate.orderType == newOrder.orderType.oppositeType

  private def selfCross(at: Price[_ <: FiatCurrency]) =
    OrderValidation.Error(NonEmptyList(OrderValidation.Violation(
      title = "Self cross detected",
      description = s"This order would be self-crossing a previously submitted order of $at"
    )))
}
