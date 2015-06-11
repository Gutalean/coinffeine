package coinffeine.protocol.events

import coinffeine.common.akka.event.TopicProvider
import coinffeine.model.network.PeerId

/** An event published when broker ID has changed. */
case class BrokerIdChanged(id: PeerId)

object BrokerIdChanged extends TopicProvider[BrokerIdChanged] {
  override val Topic = "coinffeine.network.broker-id-changed"
}
