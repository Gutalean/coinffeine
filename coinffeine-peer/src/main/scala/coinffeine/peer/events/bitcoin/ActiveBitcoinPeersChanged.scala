package coinffeine.peer.events.bitcoin

import coinffeine.common.akka.event.TopicProvider

/** An event reporting the active bitcoin peers has changed. */
case class ActiveBitcoinPeersChanged(active: Int)

object ActiveBitcoinPeersChanged extends TopicProvider[ActiveBitcoinPeersChanged]{
  override val Topic = "bitcoin.active-peers-changed"
}
