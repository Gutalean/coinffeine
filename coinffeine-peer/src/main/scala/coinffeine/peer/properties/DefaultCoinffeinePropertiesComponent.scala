package coinffeine.peer.properties

import coinffeine.model.bitcoin.MutableBitcoinProperties

trait DefaultCoinffeinePropertiesComponent extends MutableBitcoinProperties.Component {

  override lazy val bitcoinProperties = new MutableBitcoinProperties
}
