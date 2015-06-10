package coinffeine.peer.payment

import coinffeine.common.properties.{MutablePropertyMap, PropertyMap}
import coinffeine.model.currency._

trait PaymentProcessorProperties {
  val balance: PropertyMap[FiatCurrency, FiatBalance[_ <: FiatCurrency]]
}

class MutablePaymentProcessorProperties extends PaymentProcessorProperties {
  override val balance = new MutablePropertyMap[FiatCurrency, FiatBalance[_ <: FiatCurrency]]
}

object MutablePaymentProcessorProperties {

  trait Component {
    def paymentProcessorProperties: MutablePaymentProcessorProperties
  }
}
