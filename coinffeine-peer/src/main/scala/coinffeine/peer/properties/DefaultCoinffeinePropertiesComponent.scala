package coinffeine.peer.properties

import coinffeine.model.network.MutableCoinffeineNetworkProperties
import coinffeine.peer.global.MutableGlobalProperties
import coinffeine.peer.payment.MutablePaymentProcessorProperties

trait DefaultCoinffeinePropertiesComponent
    extends MutableCoinffeineNetworkProperties.Component
    with MutablePaymentProcessorProperties.Component
    with MutableGlobalProperties.Component {

  override lazy val coinffeineNetworkProperties = new MutableCoinffeineNetworkProperties
  override lazy val paymentProcessorProperties = new MutablePaymentProcessorProperties
  override lazy val globalProperties = new MutableGlobalProperties
}
