package coinffeine.peer.properties.network

import akka.actor.ActorSystem

import coinffeine.common.akka.event.{EventObservedProperty, EventObservedPropertyMap}
import coinffeine.model.network.{CoinffeineNetworkProperties, PeerId}
import coinffeine.model.order.{AnyCurrencyOrder, OrderId}
import coinffeine.peer.events.network.OrderChanged
import coinffeine.protocol.events.{BrokerIdChanged, ActiveCoinffeinePeersChanged}

class DefaultCoinffeineNetworkProperties(
    implicit system: ActorSystem) extends CoinffeineNetworkProperties {

  override val activePeers = EventObservedProperty[Int](ActiveCoinffeinePeersChanged.Topic, 0) {
    case ActiveCoinffeinePeersChanged(peers) => peers
  }

  override val brokerId = EventObservedProperty[Option[PeerId]](BrokerIdChanged.Topic, None) {
    case BrokerIdChanged(id) => Some(id)
  }

  override val orders = EventObservedPropertyMap[OrderId, AnyCurrencyOrder](OrderChanged.Topic) {
    case OrderChanged(order) => EventObservedPropertyMap.Put(order.id, order)
  }
}
