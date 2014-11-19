package coinffeine.peer.bitcoin

import org.bitcoinj.core.{Block, Peer, AbstractPeerEventListener}
import org.slf4j.LoggerFactory

import coinffeine.model.bitcoin.BlockchainStatus
import coinffeine.model.properties.MutableProperty

/** Update a BlockchainStatus property with callbacks invoked by bitcoinj */
private class BlockchainDownloadListener(status: MutableProperty[BlockchainStatus])
  extends AbstractPeerEventListener {

  override def onChainDownloadStarted(peer: Peer, blocksLeft: Int): Unit = {
    BlockchainDownloadListener.Log.debug(
      "Blockchain download started from peer {}, {} blocks to download", peer.getAddress, blocksLeft)
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

private object BlockchainDownloadListener {
  val Log = LoggerFactory.getLogger(classOf[BlockchainDownloadListener])
}


