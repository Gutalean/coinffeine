package coinffeine.peer.bitcoin

import com.typesafe.scalalogging.LazyLogging
import org.bitcoinj.core.{AbstractPeerEventListener, Block, Peer}

import coinffeine.model.bitcoin.BlockchainStatus

/** Update a BlockchainStatus property with callbacks invoked by bitcoinj */
private class BlockchainDownloadListener(publish: BlockchainStatus => Unit)
  extends AbstractPeerEventListener with LazyLogging {

  private var lastStatus: BlockchainStatus = BlockchainStatus.NotDownloading

  override def onChainDownloadStarted(peer: Peer, blocksLeft: Int): Unit = {
    logger.debug(
      s"Blockchain download started from peer ${peer.getAddress}, $blocksLeft blocks to download")
    reportDownloadProgress(blocksLeft)
  }

  override def onBlocksDownloaded(peer: Peer, block: Block, blocksLeft: Int): Unit = {
    reportDownloadProgress(blocksLeft)
  }

  def reportDownloadProgress(remainingBlocks: Int): Unit = {
    val newStatus = lastStatus match {
      case _ if remainingBlocks == 0 =>
        BlockchainStatus.NotDownloading
      case downloading @ BlockchainStatus.Downloading(blocks, _) if blocks >= remainingBlocks =>
        downloading.copy(remainingBlocks = remainingBlocks)
      case _ =>
        BlockchainStatus.Downloading(remainingBlocks, remainingBlocks)
    }
    publish(newStatus)
    lastStatus = newStatus
  }
}
