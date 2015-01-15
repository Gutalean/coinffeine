package coinffeine.overlay.relay.settings

import scala.concurrent.duration._

import coinffeine.common.DurationUtils
import coinffeine.overlay.relay.DefaultRelaySettings

/** Client configuration
  *
  * @constructor
  * @param host  Hostname of the relay network server
  * @param port  Port in which the server listens
  * @param connectionTimeout  Max time to wait for a successful connection
  * @param identificationTimeout  Timeout to kick a server that has not sent the initial network
  *                               status notification
  * @param maxFrameBytes  Maximum frame size
  */
case class RelayClientSettings(
    host: String,
    port: Int,
    connectionTimeout: FiniteDuration = DefaultRelaySettings.ConnectionTimeout,
    identificationTimeout: FiniteDuration = DefaultRelaySettings.IdentificationTimeout,
    maxFrameBytes: Int = DefaultRelaySettings.MaxFrameBytes) {
  DurationUtils.requirePositive(connectionTimeout, "The connection timeout")
  DurationUtils.requirePositive(identificationTimeout, "The identification timeout")
  require(maxFrameBytes > 0, s"Invalid max frame bytes: $maxFrameBytes")
}
