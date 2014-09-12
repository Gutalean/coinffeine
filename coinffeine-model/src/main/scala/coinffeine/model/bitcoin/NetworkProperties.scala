package coinffeine.model.bitcoin

import coinffeine.model.properties.{Property, MutableProperty}

trait NetworkProperties {

  import NetworkProperties._

  val activePeers: Property[Int]
  val blockchainStatus: Property[BlockchainStatus]
}

object NetworkProperties {

  sealed trait BlockchainStatus

  case object Connecting extends BlockchainStatus

  /** The blockchain is fully downloaded */
  case object NotDownloading extends BlockchainStatus

  /** Blockchain download is in progress */
  case class Downloading(totalBlocks: Int, remainingBlocks: Int) extends BlockchainStatus {
    require(totalBlocks > 0)

    def progress: Double = (totalBlocks - remainingBlocks) / totalBlocks.toDouble
  }
}

class MutableNetworkProperties extends NetworkProperties {

  import NetworkProperties._

  val activePeers: MutableProperty[Int] = new MutableProperty(0)
  val blockchainStatus: MutableProperty[BlockchainStatus] = new MutableProperty(Connecting)
}
