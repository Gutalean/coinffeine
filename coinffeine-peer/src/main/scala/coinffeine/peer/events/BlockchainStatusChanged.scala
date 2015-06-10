package coinffeine.peer.events

import coinffeine.model.bitcoin.BlockchainStatus

case class BlockchainStatusChanged(status: BlockchainStatus)

object BlockchainStatusChanged {

  val Topic = "bitcoin.blockchain.status-changed"
}
