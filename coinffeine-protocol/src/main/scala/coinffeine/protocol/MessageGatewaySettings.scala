package coinffeine.protocol

import scala.concurrent.duration.FiniteDuration

import coinffeine.model.network.PeerId

case class MessageGatewaySettings(peerId: PeerId, connectionRetryInterval: FiniteDuration)
