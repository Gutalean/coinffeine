package coinffeine.peer.payment

import coinffeine.common.properties.PropertyMap
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.currency.balance.FiatBalance

trait PaymentProcessorProperties {
  val balances: PropertyMap[FiatCurrency, FiatBalance]
}
