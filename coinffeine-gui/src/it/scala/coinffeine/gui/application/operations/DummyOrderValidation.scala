package coinffeine.gui.application.operations

import coinffeine.gui.application.operations.validation.OrderValidation
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.Order

object DummyOrderValidation extends OrderValidation {
  override def apply[C <: FiatCurrency](newOrder: Order[C]) = OrderValidation.OK
}
