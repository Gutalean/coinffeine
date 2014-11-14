package coinffeine.protocol

import java.net.NetworkInterface
import scala.concurrent.duration.FiniteDuration

import coinffeine.model.network.{NetworkEndpoint, PeerId}

case class MessageGatewaySettings(
  peerId: Option[PeerId],
  peerPort: Int,
  brokerEndpoint: NetworkEndpoint,
  ignoredNetworkInterfaces: Seq[NetworkInterface],
  connectionRetryInterval: FiniteDuration,
  externalEndpoint: Option[NetworkEndpoint]
)
