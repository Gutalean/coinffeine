package coinffeine.peer.bitcoin

import com.typesafe.scalalogging.LazyLogging
import org.bitcoinj.core.{AbstractPeerEventListener, Block, Peer}

import coinffeine.common.properties.MutableProperty
import coinffeine.model.bitcoin.BlockchainStatus

/** Update a BlockchainStatus property with callbacks invoked by bitcoinj */
private class BlockchainDownloadListener(status: MutableProperty[BlockchainStatus])
  extends AbstractPeerEventListener with LazyLogging {

  override def onChainDownloadStarted(peer: Peer, blocksLeft: Int): Unit = {
    logger.debug(
      s"Blockchain download started from peer ${peer.getAddress}, $blocksLeft blocks to download")
    reportDownloadProgress(blocksLeft)
  }

  override def onBlocksDownloaded(peer: Peer, block: Block, blocksLeft: Int): Unit = {
    reportDownloadProgress(blocksLeft)
  }

  def reportDownloadProgress(remainingBlocks: Int): Unit = {
    status.set(status.get match {
      case _ if remainingBlocks == 0 =>
        BlockchainStatus.NotDownloading
      case downloading @ BlockchainStatus.Downloading(blocks, _) if blocks >= remainingBlocks =>
        downloading.copy(remainingBlocks = remainingBlocks)
      case _ =>
        BlockchainStatus.Downloading(remainingBlocks, remainingBlocks)
    })
  }
}
