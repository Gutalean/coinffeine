package coinffeine.model.bitcoin

import org.joda.time.DateTime

sealed trait BlockchainStatus

object BlockchainStatus {

  case class BlockInfo(height: Long, date: DateTime)

  /** The blockchain is fully downloaded */
  case class NotDownloading(lastBlock: Option[BlockInfo]) extends BlockchainStatus

  /** Blockchain download is in progress.
    *
    * @param totalBlocks      Total blocks to download. It can't be negative but can be 0
    * @param remainingBlocks  Blocks still pending to be downloaded
    */
  case class Downloading(totalBlocks: Int, remainingBlocks: Int) extends BlockchainStatus {
    require(totalBlocks >= 0, s"Total blocks should be non-negative ($totalBlocks given)")

    def progress: Double =
      if (totalBlocks == 0) 1
      else (totalBlocks - remainingBlocks) / totalBlocks.toDouble
  }
}
