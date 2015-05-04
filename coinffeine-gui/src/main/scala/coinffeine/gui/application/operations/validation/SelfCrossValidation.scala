package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList

import coinffeine.gui.application.operations.validation.OrderValidation.Result
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market._
import coinffeine.model.properties.PropertyMap

private class SelfCrossValidation(orders: PropertyMap[OrderId, AnyCurrencyOrder])
  extends OrderValidation {

  override def apply[C <: FiatCurrency](request: OrderRequest[C], spread: Spread[C]) =
    request.price match {
      case LimitPrice(limit) => validateLimitOrder(request, limit)
      case MarketPrice(_) => OrderValidation.OK
    }

  private def validateLimitOrder[C <: FiatCurrency](request: OrderRequest[C],
                                                    limit: Price[C]): Result = {
    val priceCrosses = request.orderType match {
      case Bid => limit.outbidsOrMatches _
      case Ask => limit.underbidsOrMatches _
    }
    val crossedPrices = for {
      candidateOrder <- orders.values if suitableForSelfCross(request, candidateOrder)
      crossPrice = candidateOrder.price.toOption.get.asInstanceOf[Price[C]]
      if priceCrosses(crossPrice)
    } yield crossPrice
    crossedPrices.headOption.fold[Result](OrderValidation.OK)(selfCross)
  }

  private def suitableForSelfCross(request: OrderRequest[_ <: FiatCurrency],
                                   candidate: AnyCurrencyOrder): Boolean =
    candidate.price.isLimited && candidate.status.isActive &&
      candidate.price.currency == request.price.currency &&
      candidate.orderType == request.orderType.oppositeType

  private def selfCross(at: Price[_ <: FiatCurrency]) = OrderValidation.Error(NonEmptyList(
    s"This order would be self-crossing a previously submitted order of $at"))
}
