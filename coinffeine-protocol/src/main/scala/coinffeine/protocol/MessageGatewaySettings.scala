package coinffeine.protocol

import java.net.NetworkInterface

case class MessageGatewaySettings(
  peerPort: Int,
  brokerHost: String,
  brokerPort: Int,
  ignoredNetworkInterfaces: Seq[NetworkInterface]
)
