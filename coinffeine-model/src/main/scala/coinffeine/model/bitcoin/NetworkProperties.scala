package coinffeine.model.bitcoin

import coinffeine.common.properties.{MutableProperty, Property}

trait NetworkProperties {

  val activePeers: Property[Int]
  val blockchainStatus: Property[BlockchainStatus]
}

class MutableNetworkProperties extends NetworkProperties {

  val activePeers: MutableProperty[Int] = new MutableProperty(0)
  val blockchainStatus: MutableProperty[BlockchainStatus] =
    new MutableProperty(BlockchainStatus.NotDownloading(lastBlock = None))
}
