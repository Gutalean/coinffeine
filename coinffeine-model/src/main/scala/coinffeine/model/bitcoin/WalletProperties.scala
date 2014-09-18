package coinffeine.model.bitcoin

import coinffeine.model.currency.BitcoinBalance
import coinffeine.model.properties.{MutableProperty, Property}

trait WalletProperties {
  val balance: Property[Option[BitcoinBalance]]
  val primaryAddress: Property[Option[Address]]
  val transactions: Property[Seq[ImmutableTransaction]]
}

class MutableWalletProperties extends WalletProperties {

  override val balance: MutableProperty[Option[BitcoinBalance]] =
    new MutableProperty(None)

  override val primaryAddress: MutableProperty[Option[Address]] =
    new MutableProperty(None)

  override val transactions: MutableProperty[Seq[ImmutableTransaction]] =
    new MutableProperty(Seq.empty)
}
