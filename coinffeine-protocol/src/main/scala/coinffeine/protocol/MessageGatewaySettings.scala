package coinffeine.protocol

import java.net.NetworkInterface

import coinffeine.model.network.PeerId

case class MessageGatewaySettings(
  peerId: Option[PeerId],
  peerPort: Int,
  brokerHost: String,
  brokerPort: Int,
  ignoredNetworkInterfaces: Seq[NetworkInterface]
)
