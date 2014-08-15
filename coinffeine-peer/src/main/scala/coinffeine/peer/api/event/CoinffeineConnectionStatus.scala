package coinffeine.peer.api.event

/** An event reporting the state of the connection with the Coinffeine network. */
case class CoinffeineConnectionStatus(activePeers: Int) {

  def connected: Boolean = activePeers > 0
}
