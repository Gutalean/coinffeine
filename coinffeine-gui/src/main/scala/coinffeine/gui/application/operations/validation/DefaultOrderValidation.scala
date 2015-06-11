package coinffeine.gui.application.operations.validation

import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.model.order.OrderRequest
import coinffeine.peer.api.CoinffeineApp

class DefaultOrderValidation(app: CoinffeineApp) extends OrderValidation {

  private val amountsCalculator = app.utils.exchangeAmountsCalculator

  private val validations = Seq(
    new SelfCrossValidation(app.operations.orders),
    new MaximumFiatValidation(amountsCalculator),
    new AvailableFundsValidation(
      amountsCalculator, app.paymentProcessor.balance, app.wallet.balance)
  )

  override def apply[C <: FiatCurrency](request: OrderRequest[C], spread: Spread[C]) =
    validations.map(_.apply(request, spread)).reduce(OrderValidation.Result.combine)
}
