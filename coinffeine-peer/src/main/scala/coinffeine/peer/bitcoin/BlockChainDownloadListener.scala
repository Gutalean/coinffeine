package coinffeine.peer.bitcoin

import com.typesafe.scalalogging.LazyLogging
import org.bitcoinj.core.{AbstractBlockChain, AbstractPeerEventListener, Block, Peer}
import org.joda.time.DateTime

import coinffeine.model.bitcoin.BlockchainStatus
import coinffeine.model.bitcoin.BlockchainStatus.BlockInfo

/** Update a BlockchainStatus property with callbacks invoked by bitcoinj */
private class BlockchainDownloadListener(
    blockchain: AbstractBlockChain, publish: BlockchainStatus => Unit)
  extends AbstractPeerEventListener with LazyLogging {

  private var lastStatus: BlockchainStatus = BlockchainStatus.NotDownloading(None)

  override def onChainDownloadStarted(peer: Peer, blocksLeft: Int): Unit = {
    logger.info(
      s"Blockchain download started from peer ${peer.getAddress}, $blocksLeft blocks to download")
    val lastBlockInfo = BlockInfo(
      peer.getBestHeight, new DateTime(blockchain.getChainHead.getHeader.getTime))
    reportDownloadProgress(Some(lastBlockInfo), blocksLeft)
  }

  override def onBlocksDownloaded(peer: Peer, block: Block, blocksLeft: Int): Unit = {
    val lastBlockInfo = BlockInfo(peer.getBestHeight, new DateTime(block.getTime))
    reportDownloadProgress(Some(lastBlockInfo), blocksLeft)
  }

  def reportDownloadProgress(
      lastBlock: Option[BlockchainStatus.BlockInfo], remainingBlocks: Int): Unit = {
    val newStatus = lastStatus match {
      case _ if remainingBlocks == 0 =>
        logger.info("Blockchain download completed")
        BlockchainStatus.NotDownloading(lastBlock)
      case downloading @ BlockchainStatus.Downloading(blocks, _) if blocks >= remainingBlocks =>
        downloading.copy(remainingBlocks = remainingBlocks)
      case _ =>
        BlockchainStatus.Downloading(remainingBlocks, remainingBlocks)
    }
    publish(newStatus)
    lastStatus = newStatus
  }
}
