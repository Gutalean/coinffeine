package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market._
import coinffeine.model.properties.PropertyMap

private class SelfCrossValidation(orders: PropertyMap[OrderId, AnyCurrencyOrder])
  extends OrderValidation {

  override def apply[C <: FiatCurrency](newOrder: Order[C]) = {
    val limitPrice = limitPriceOf(newOrder)
    val priceCrosses = newOrder.orderType match {
      case Bid => limitPrice.outbidsOrMatches _
      case Ask => limitPrice.underbidsOrMatches _
    }
    val crossedPrices = for {
      candidateOrder <- orders.values
      if suitableForSelfCross(newOrder, candidateOrder)
      crossPrice = limitPriceOf(candidateOrder).asInstanceOf[Price[C]]
      if priceCrosses(crossPrice)
    } yield crossPrice
    crossedPrices.headOption.fold[OrderValidation.Result](OrderValidation.OK)(selfCross)
  }

  private def suitableForSelfCross(newOrder: Order[_ <: FiatCurrency],
                                   candidate: AnyCurrencyOrder): Boolean =
    candidate.price.currency == newOrder.price.currency && candidate.status.isActive &&
      candidate.orderType == newOrder.orderType.oppositeType

  private def selfCross(at: Price[_ <: FiatCurrency]) =
    OrderValidation.Error(NonEmptyList(OrderValidation.Violation(
      title = "Self cross detected",
      description = s"This order would be self-crossing a previously submitted order of $at"
    )))

  private def limitPriceOf[C <: FiatCurrency](order: Order[C]): Price[C] =
    order.price.toOption.getOrElse(
      throw new UnsupportedOperationException("Market-price orders are not yet supported"))
}
