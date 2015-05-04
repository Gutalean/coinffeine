package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.market.OrderRequest
import coinffeine.peer.amounts.AmountsCalculator

private class MaximumFiatValidation(amountsCalculator: AmountsCalculator) extends OrderValidation {

  override def apply[C <: FiatCurrency](request: OrderRequest[C]): OrderValidation.Result = {
    val maximum = amountsCalculator.maxFiatPerExchange(request.price.currency)
    val tooHighRequestOpt = for {
      price <- request.price.toOption
      requestedFiat = price.of(request.amount)
      if requestedFiat > maximum
    } yield requestedFiat
    tooHighRequestOpt.fold[OrderValidation.Result](OrderValidation.OK)(amount =>
      maximumAmountViolated(amount, maximum)
    )
  }

  private def maximumAmountViolated(requested: CurrencyAmount[_], maximum: CurrencyAmount[_]) =
    OrderValidation.Error(NonEmptyList(
      s"Maximum allowed fiat amount is $maximum, but you requested $requested"))
}
