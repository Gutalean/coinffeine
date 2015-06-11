package coinffeine.peer.properties

import coinffeine.peer.global.MutableGlobalProperties
import coinffeine.peer.payment.MutablePaymentProcessorProperties

trait DefaultCoinffeinePropertiesComponent
    extends MutablePaymentProcessorProperties.Component
    with MutableGlobalProperties.Component {

  override lazy val paymentProcessorProperties = new MutablePaymentProcessorProperties
  override lazy val globalProperties = new MutableGlobalProperties
}
