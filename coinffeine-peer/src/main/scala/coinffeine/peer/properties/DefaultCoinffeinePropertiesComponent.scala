package coinffeine.peer.properties

import coinffeine.model.bitcoin.MutableBitcoinProperties
import coinffeine.model.network.MutableCoinffeineNetworkProperties

trait DefaultCoinffeinePropertiesComponent
    extends MutableBitcoinProperties.Component
    with MutableCoinffeineNetworkProperties.Component {

  override lazy val bitcoinProperties = new MutableBitcoinProperties
  override lazy val coinffeineNetworkProperties = new MutableCoinffeineNetworkProperties
}
