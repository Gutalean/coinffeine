package coinffeine.model.bitcoin

import coinffeine.model.currency.BitcoinBalance
import coinffeine.model.properties.{MutableProperty, Property}

trait WalletProperties {
  val balance: Property[Option[BitcoinBalance]]
  val primaryAddress: Property[Option[Address]]
}

class MutableWalletProperties extends WalletProperties {
  override val balance: MutableProperty[Option[BitcoinBalance]] = new MutableProperty(None)
  override val primaryAddress: MutableProperty[Option[Address]] = new MutableProperty(None)
}
