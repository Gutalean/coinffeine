package coinffeine.peer.payment

import coinffeine.common.properties.PropertyMap
import coinffeine.model.currency.balance.FiatBalance
import coinffeine.model.currency.{_}

trait PaymentProcessorProperties {
  val balance: PropertyMap[FiatCurrency, FiatBalance]
}
