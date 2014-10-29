package coinffeine.protocol

import java.net.NetworkInterface
import scala.concurrent.duration.FiniteDuration

import coinffeine.model.network.PeerId

case class MessageGatewaySettings(
  peerId: Option[PeerId],
  peerPort: Int,
  brokerHost: String,
  brokerPort: Int,
  ignoredNetworkInterfaces: Seq[NetworkInterface],
  connectionRetryInterval: FiniteDuration
)
