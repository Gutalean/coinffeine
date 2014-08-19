package coinffeine.model.event

import coinffeine.model.network.PeerId

/** An event reporting the state of the connection with the Coinffeine network. */
case class CoinffeineConnectionStatus(activePeers: Int = 0, brokerId: Option[PeerId] = None)
  extends CoinffeineAppEvent {

  def connected: Boolean = activePeers > 0 && brokerId.isDefined
}
