package coinffeine.peer.events.bitcoin

import coinffeine.model.bitcoin.BlockchainStatus

/** An event reporting the blockchain status has changed. */
case class BlockchainStatusChanged(status: BlockchainStatus)

object BlockchainStatusChanged {
  val Topic = "bitcoin.blockchain.status-changed"
}
