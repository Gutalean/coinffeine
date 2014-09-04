package coinffeine.peer.bitcoin

import com.google.bitcoin.core._

import coinffeine.model.bitcoin._
import coinffeine.peer.config.ConfigComponent

/** Dummy wallet that is not persisted and just load a private key from a configuration file */
trait DummyPrivateKeysComponent extends PrivateKeysComponent {
  this: NetworkComponent with ConfigComponent =>

  override lazy val keyPairs: Seq[KeyPair] = Seq(parsePrivateKey())

  private def parsePrivateKey(): KeyPair =
    new DumpedPrivateKey(network, settingsProvider.bitcoinSettings.walletPrivateKey).getKey
}
