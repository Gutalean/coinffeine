package coinffeine.gui.application.properties

import scalafx.beans.property.ReadOnlyObjectProperty

import coinffeine.model.currency._
import coinffeine.model.currency.BitcoinBalance
import coinffeine.peer.api.CoinffeineWallet

class WalletProperties(wallet: CoinffeineWallet) extends PropertyBindings {

  val balance: ReadOnlyObjectProperty[Option[BitcoinBalance]] =
    createBounded(wallet.balance, "WalletBalance")

  val activities = new WalletActivityProperties(wallet)
}
