package coinffeine.peer.api.impl

import coinffeine.model.bitcoin.NetworkProperties
import coinffeine.peer.api.BitcoinNetwork

class DefaultBitcoinNetwork(properties: NetworkProperties) extends BitcoinNetwork {

  override val activePeers = properties.activePeers
  override val blockchainStatus = properties.blockchainStatus
}
