package coinffeine.peer.properties

import coinffeine.model.bitcoin.MutableBitcoinProperties
import coinffeine.model.network.MutableCoinffeineNetworkProperties
import coinffeine.peer.payment.MutablePaymentProcessorProperties
import coinffeine.peer.global.MutableGlobalProperties

trait DefaultCoinffeinePropertiesComponent
    extends MutableBitcoinProperties.Component
    with MutableCoinffeineNetworkProperties.Component
    with MutablePaymentProcessorProperties.Component
    with MutableGlobalProperties.Component {

  override lazy val bitcoinProperties = new MutableBitcoinProperties
  override lazy val coinffeineNetworkProperties = new MutableCoinffeineNetworkProperties
  override lazy val paymentProcessorProperties = new MutablePaymentProcessorProperties
  override lazy val globalProperties = new MutableGlobalProperties
}
