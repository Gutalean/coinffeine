package coinffeine.gui.application.properties

import scalafx.collections.ObservableBuffer

import coinffeine.model.bitcoin.Hash
import coinffeine.peer.api.CoinffeineWallet

class WalletActivityProperties(wallet: CoinffeineWallet)
    extends ObservableBuffer[WalletActivityEntryProperties] {

  import coinffeine.gui.util.FxExecutor.asContext

  wallet.activity.onNewValue { activity =>
    activity.entries.foreach { entry => find(entry.tx.get.getHash) match {
      case Some(txProps) => txProps.update(entry)
      case None => this += new WalletActivityEntryProperties(entry)
    }}
  }

  private def find(hash: Hash): Option[WalletActivityEntryProperties] =
    find(_.view.value.hash == hash)
}
