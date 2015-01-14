package coinffeine.overlay.relay.settings

import scala.concurrent.duration._

import coinffeine.overlay.relay.DefaultRelaySettings

case class RelaySettings(
    serverAddress: String,
    serverPort: Int,
    maxFrameBytes: Int = DefaultRelaySettings.MaxFrameBytes,
    connectionTimeout: FiniteDuration = DefaultRelaySettings.ConnectionTimeout,
    identificationTimeout: FiniteDuration = DefaultRelaySettings.IdentificationTimeout,
    minTimeBetweenStatusUpdates: FiniteDuration = DefaultRelaySettings.MinTimeBetweenStatusUpdates) {
  require(maxFrameBytes > 0, s"Invalid max frame bytes: $maxFrameBytes")
  require(connectionTimeout > 0.seconds, "The connection timeout cannot be zero")

  def clientSettings: RelayClientSettings =
    RelayClientSettings(serverAddress, serverPort, connectionTimeout, maxFrameBytes)

  def serverSettings: RelayServerSettings = RelayServerSettings(
    serverAddress, serverPort, maxFrameBytes, identificationTimeout, minTimeBetweenStatusUpdates)
}
