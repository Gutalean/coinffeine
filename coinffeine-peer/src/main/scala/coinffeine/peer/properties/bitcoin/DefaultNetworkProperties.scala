package coinffeine.peer.properties.bitcoin

import akka.actor.ActorSystem

import coinffeine.common.akka.event.EventObservedProperty
import coinffeine.model.bitcoin.{BlockchainStatus, NetworkProperties}
import coinffeine.peer.events.bitcoin.{ActiveBitcoinPeersChanged, BlockchainStatusChanged}

class DefaultNetworkProperties(implicit system: ActorSystem) extends NetworkProperties {

  override val activePeers = EventObservedProperty(ActiveBitcoinPeersChanged.Topic, 0) {
    case ActiveBitcoinPeersChanged(active) => active
  }

  override val blockchainStatus = EventObservedProperty[BlockchainStatus](
      BlockchainStatusChanged.Topic, BlockchainStatus.NotDownloading) {
    case BlockchainStatusChanged(to) => to
  }
}
