package coinffeine.overlay.relay.settings

import scala.concurrent.duration._

import coinffeine.common.DurationUtils
import coinffeine.overlay.relay.DefaultRelaySettings

case class RelaySettings(
    serverAddress: String,
    serverPort: Int,
    maxFrameBytes: Int = DefaultRelaySettings.MaxFrameBytes,
    connectionTimeout: FiniteDuration = DefaultRelaySettings.ConnectionTimeout,
    identificationTimeout: FiniteDuration = DefaultRelaySettings.IdentificationTimeout,
    minTimeBetweenStatusUpdates: FiniteDuration = DefaultRelaySettings.MinTimeBetweenStatusUpdates) {

  require(maxFrameBytes > 0, s"Invalid max frame bytes: $maxFrameBytes")
  DurationUtils.requirePositive(connectionTimeout, "The connection timeout")
  DurationUtils.requirePositive(identificationTimeout, "The identification timeout")

  def clientSettings: RelayClientSettings = RelayClientSettings(
    serverAddress, serverPort, connectionTimeout, identificationTimeout, maxFrameBytes)

  def serverSettings: RelayServerSettings = RelayServerSettings(
    serverAddress, serverPort, maxFrameBytes, identificationTimeout, minTimeBetweenStatusUpdates)
}
