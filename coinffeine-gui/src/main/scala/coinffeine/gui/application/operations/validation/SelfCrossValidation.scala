package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList

import coinffeine.common.properties.PropertyMap
import coinffeine.gui.application.operations.validation.OrderValidation.Result
import coinffeine.model.market._
import coinffeine.model.order._

private class SelfCrossValidation(orders: PropertyMap[OrderId, Order])
  extends OrderValidation {

  override def apply(request: OrderRequest, spread: Spread) =
    request.price match {
      case LimitPrice(limit) => validateLimitOrder(request, limit)
      case MarketPrice(_) => OrderValidation.OK
    }

  private def validateLimitOrder(request: OrderRequest,
                                                    limit: Price): Result = {
    val priceCrosses = request.orderType match {
      case Bid => limit.outbidsOrMatches _
      case Ask => limit.underbidsOrMatches _
    }
    val crossedPrices = for {
      candidateOrder <- orders.values if suitableForSelfCross(request, candidateOrder)
      crossPrice = candidateOrder.price.toOption.get.asInstanceOf[Price]
      if priceCrosses(crossPrice)
    } yield crossPrice
    crossedPrices.headOption.fold[Result](OrderValidation.OK)(selfCross)
  }

  private def suitableForSelfCross(request: OrderRequest,
                                   candidate: Order): Boolean =
    candidate.price.isLimited && candidate.status.isActive &&
      candidate.price.currency == request.price.currency &&
      candidate.orderType == request.orderType.oppositeType

  private def selfCross(at: Price) = OrderValidation.Error(NonEmptyList(
    s"This order would be self-crossing a previously submitted order of $at"))
}
