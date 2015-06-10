package coinffeine.model.bitcoin

import coinffeine.common.properties.Property
import coinffeine.model.currency.BitcoinBalance

trait WalletProperties {
  val balance: Property[Option[BitcoinBalance]]
  val primaryAddress: Property[Option[Address]]
  val activity: Property[WalletActivity]
}
