package coinffeine.gui.application.operations.validation

import coinffeine.model.currency.FiatAmount
import coinffeine.model.market.Spread
import coinffeine.model.order.OrderRequest
import coinffeine.peer.amounts.AmountsCalculator

private class MaximumFiatValidation(amountsCalculator: AmountsCalculator) extends OrderValidation {

  override def apply(request: OrderRequest, spread: Spread): OrderValidation.Result = {
    val maximum = amountsCalculator.maxFiatPerExchange(request.price.currency)
    val tooHighRequestOpt = for {
      price <- request.estimatedPrice(spread)
      requestedFiat = price.of(request.amount)
      if requestedFiat > maximum
    } yield requestedFiat
    tooHighRequestOpt.fold(OrderValidation.Ok)(amount =>
      maximumAmountViolated(amount, maximum)
    )
  }

  private def maximumAmountViolated(requested: FiatAmount, maximum: FiatAmount) =
    OrderValidation.error(
      s"Maximum allowed fiat amount is $maximum, but you requested $requested")
}
