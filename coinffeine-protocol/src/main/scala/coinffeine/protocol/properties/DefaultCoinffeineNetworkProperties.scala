package coinffeine.protocol.properties

import akka.actor.ActorSystem

import coinffeine.common.akka.event.EventObservedProperty
import coinffeine.model.network.{CoinffeineNetworkProperties, PeerId}
import coinffeine.protocol.events.{ActiveCoinffeinePeersChanged, BrokerIdChanged}

class DefaultCoinffeineNetworkProperties(implicit system: ActorSystem)
  extends CoinffeineNetworkProperties {

  override val activePeers = EventObservedProperty[Int](ActiveCoinffeinePeersChanged.Topic, 0) {
    case ActiveCoinffeinePeersChanged(peers) => peers
  }

  override val brokerId = EventObservedProperty[Option[PeerId]](BrokerIdChanged.Topic, None) {
    case BrokerIdChanged(id) => Some(id)
  }
}
