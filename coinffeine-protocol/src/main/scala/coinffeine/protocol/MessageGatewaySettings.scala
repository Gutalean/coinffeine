package coinffeine.protocol

import scala.concurrent.duration.FiniteDuration

import coinffeine.model.network.{NetworkEndpoint, PeerId}

case class MessageGatewaySettings(
  peerId: PeerId,
  peerPort: Int,
  brokerEndpoint: NetworkEndpoint,
  connectionRetryInterval: FiniteDuration,
  externalForwardedPort: Option[Int]
)
