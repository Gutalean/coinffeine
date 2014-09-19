package coinffeine.gui.application.properties

import scalafx.collections.ObservableBuffer

import com.google.bitcoin.core.Sha256Hash

import coinffeine.peer.api.CoinffeineWallet

class WalletActivityProperties(wallet: CoinffeineWallet)
    extends ObservableBuffer[WalletActivityEntryProperties] {

  import coinffeine.gui.util.FxExecutor.asContext

  def find(hash: Sha256Hash): Option[WalletActivityEntryProperties] = find(_.hasHash(hash))

  wallet.activity.onNewValue { activity =>
    activity.entries.foreach { entry => find(entry.tx.get.getHash) match {
      case Some(txProps) => txProps.update(entry)
      case None => this += new WalletActivityEntryProperties(entry)
    }}
  }
}
