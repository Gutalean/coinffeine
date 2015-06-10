package coinffeine.peer.events.bitcoin

/** An event reporting the active bitcoin peers has changed. */
case class ActiveBitcoinPeersChanged(active: Int)

object ActiveBitcoinPeersChanged {
  val Topic = "bitcoin.active-peers-changed"
}
