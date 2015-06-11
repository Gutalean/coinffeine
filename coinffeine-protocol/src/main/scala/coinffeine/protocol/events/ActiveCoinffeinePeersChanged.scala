package coinffeine.protocol.events

import coinffeine.common.akka.event.TopicProvider

/** An eventy published when active Coinffeine peers has changed. */
case class ActiveCoinffeinePeersChanged(activePeers: Int)

object ActiveCoinffeinePeersChanged extends TopicProvider[ActiveCoinffeinePeersChanged] {
  override val Topic = "coinffeine.network.active-peers-changed"
}
