package coinffeine.model.bitcoin

import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Implicits._
import coinffeine.model.properties.{Property, MutableProperty}

trait WalletProperties {
  def balance: Property[Option[BitcoinAmount]]
  def primaryKeyPair: Property[Option[Address]]
}

class MutableWalletProperties extends WalletProperties {
  override val balance: MutableProperty[Option[BitcoinAmount]] = new MutableProperty(None)
  override val primaryKeyPair: MutableProperty[Option[Address]] = new MutableProperty(None)
}
