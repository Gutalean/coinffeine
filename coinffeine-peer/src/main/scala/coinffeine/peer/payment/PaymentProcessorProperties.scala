package coinffeine.peer.payment

import coinffeine.model.currency._
import coinffeine.model.properties.{MutablePropertyMap, PropertyMap}

trait PaymentProcessorProperties {
  val balance: PropertyMap[FiatCurrency, Balance[_ <: FiatCurrency]]
}

class MutablePaymentProcessorProperties extends PaymentProcessorProperties {
  override val balance = new MutablePropertyMap[FiatCurrency, Balance[_ <: FiatCurrency]]
}

object MutablePaymentProcessorProperties {

  trait Component {
    def paymentProcessorProperties: MutablePaymentProcessorProperties
  }
}
