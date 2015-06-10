package coinffeine.model.bitcoin

import coinffeine.common.properties.{MutableProperty, Property}
import coinffeine.model.currency.BitcoinBalance

trait WalletProperties {
  val balance: Property[Option[BitcoinBalance]]
  val primaryAddress: Property[Option[Address]]
  val activity: Property[WalletActivity]
}

class MutableWalletProperties extends WalletProperties {

  override val balance: MutableProperty[Option[BitcoinBalance]] =
    new MutableProperty(None)

  override val primaryAddress: MutableProperty[Option[Address]] =
    new MutableProperty(None)

  override val activity: MutableProperty[WalletActivity] =
    new MutableProperty(WalletActivity())
}
