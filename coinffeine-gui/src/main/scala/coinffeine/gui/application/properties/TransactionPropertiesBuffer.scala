package coinffeine.gui.application.properties

import scalafx.collections.ObservableBuffer

import com.google.bitcoin.core.Sha256Hash

import coinffeine.peer.api.CoinffeineWallet

class TransactionPropertiesBuffer(wallet: CoinffeineWallet)
    extends ObservableBuffer[TransactionProperties] {

  import coinffeine.gui.util.FxExecutor.asContext

  def find(hash: Sha256Hash): Option[TransactionProperties] = find(_.hasHash(hash))

  wallet.transactions.onNewValue { transactions =>
    transactions.foreach { tx => find(tx.get.getHash) match {
      case Some(txProps) => txProps.update(tx)
      case None => this += new TransactionProperties(tx)
    }}
  }
}
