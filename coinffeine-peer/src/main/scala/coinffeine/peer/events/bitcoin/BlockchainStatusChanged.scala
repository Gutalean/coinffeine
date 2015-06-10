package coinffeine.peer.events.bitcoin

import coinffeine.common.akka.event.TopicProvider
import coinffeine.model.bitcoin.BlockchainStatus

/** An event reporting the blockchain status has changed. */
case class BlockchainStatusChanged(status: BlockchainStatus)

object BlockchainStatusChanged extends TopicProvider[BlockchainStatusChanged] {
  override val Topic = "bitcoin.blockchain.status-changed"
}
