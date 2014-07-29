package coinffeine.protocol.messages.brokerage

import coinffeine.protocol.messages.PublicMessage

/** Represents a message from the broker indicating that the peer positions were received.
  *
  * @param nonce The nonce of the [[PeerPositions]] that were received.
  */
case class PeerPositionsReceived(nonce: PeerPositions.Nonce) extends PublicMessage
