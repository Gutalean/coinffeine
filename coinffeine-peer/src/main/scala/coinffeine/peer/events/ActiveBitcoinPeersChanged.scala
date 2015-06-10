package coinffeine.peer.events

case class ActiveBitcoinPeersChanged(active: Int)

object ActiveBitcoinPeersChanged {
  val Topic = "bitcoin.active-peers-changed"
}
