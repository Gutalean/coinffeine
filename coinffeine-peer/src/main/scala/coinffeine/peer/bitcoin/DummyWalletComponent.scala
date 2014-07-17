package coinffeine.peer.bitcoin

import com.google.bitcoin.core._

import coinffeine.model.bitcoin._
import coinffeine.peer.config.ConfigComponent

/** Dummy wallet that is not persisted and just load a private key from a configuration file */
trait DummyWalletComponent extends WalletComponent {
  this: NetworkComponent with BitcoinPeerActor.Component with BlockchainComponent
    with ConfigComponent =>

  override lazy val wallet: Wallet = {
    val w = new Wallet(network)
    w.addKey(parsePrivateKey())
    setupWallet(w)
    w
  }

  private def setupWallet(wallet: Wallet): Unit = {
    blockchain.addWallet(wallet)
    peerGroup.addWallet(wallet)
  }

  private def parsePrivateKey(): KeyPair =
    new DumpedPrivateKey(network, config.getString("coinffeine.wallet.key")).getKey
}
