package coinffeine.overlay.relay

import akka.util.ByteString

import coinffeine.overlay.OverlayId

private sealed trait Message
private case class JoinMessage(id: OverlayId) extends Message
private case class StatusMessage(networkSize: Int) extends Message {
  require(networkSize >= 0, s"Invalid network size: $networkSize")
}
private case class RelayMessage(id: OverlayId, payload: ByteString) extends Message
