package coinffeine.peer.payment

import coinffeine.common.properties.PropertyMap
import coinffeine.model.currency.{FiatBalance, _}

trait PaymentProcessorProperties {
  val balance: PropertyMap[FiatCurrency, FiatBalance]
}
