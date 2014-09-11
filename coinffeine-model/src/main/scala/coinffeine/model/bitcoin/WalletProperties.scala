package coinffeine.model.bitcoin

import coinffeine.model.currency.BitcoinBalance
import coinffeine.model.properties.{MutableProperty, Property}

trait WalletProperties {
  val balance: Property[Option[BitcoinBalance]]
  val primaryKeyPair: Property[Option[Address]]
}

class MutableWalletProperties extends WalletProperties {
  override val balance: MutableProperty[Option[BitcoinBalance]] = new MutableProperty(None)
  override val primaryKeyPair: MutableProperty[Option[Address]] = new MutableProperty(None)
}
