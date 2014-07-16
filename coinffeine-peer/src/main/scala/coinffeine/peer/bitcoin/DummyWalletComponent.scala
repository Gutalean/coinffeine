package coinffeine.peer.bitcoin

import com.google.bitcoin.core.DumpedPrivateKey

import coinffeine.model.bitcoin._
import coinffeine.peer.config.ConfigComponent

/** Dummy wallet that is not persisted and just load a private key from a configuration file */
trait DummyWalletComponent extends WalletComponent {
  this: NetworkComponent with BlockchainComponent with ConfigComponent =>

  override lazy val wallet: Wallet = {
    val w = new Wallet(network)
    w.addKey(parsePrivateKey())
    blockchain.addWallet(w)
    w
  }

  private def parsePrivateKey(): KeyPair =
    new DumpedPrivateKey(network, config.getString("coinffeine.wallet.key")).getKey
}
