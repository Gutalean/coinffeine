package coinffeine.overlay.relay.messages

import akka.util.ByteString

import coinffeine.overlay.OverlayId

private[relay] sealed trait Message
private[relay] case class JoinMessage(id: OverlayId) extends Message
private[relay] case class StatusMessage(networkSize: Int) extends Message {
  require(networkSize >= 0, s"Invalid network size: $networkSize")
}
private[relay] case class RelayMessage(id: OverlayId, payload: ByteString) extends Message

