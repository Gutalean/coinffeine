package coinffeine.peer.bitcoin

import coinffeine.model.bitcoin.{KeyPair, Wallet}

trait PrivateKeysComponent {
  def keyPairs: Seq[KeyPair]
}
