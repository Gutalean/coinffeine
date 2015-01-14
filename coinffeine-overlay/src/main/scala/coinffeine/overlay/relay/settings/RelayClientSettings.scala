package coinffeine.overlay.relay.settings

import scala.concurrent.duration._

import coinffeine.overlay.relay.DefaultRelaySettings

/** Client configuration
  *
  * @constructor
  * @param host  Hostname of the relay network server
  * @param port  Port in which the server listens
  * @param connectionTimeout  Max time to wait for a successful connection
  * @param maxFrameBytes  Maximum frame size
  */
case class RelayClientSettings(
    host: String,
    port: Int,
    connectionTimeout: FiniteDuration = DefaultRelaySettings.ConnectionTimeout,
    maxFrameBytes: Int = DefaultRelaySettings.MaxFrameBytes) {
  require(maxFrameBytes > 0, s"Invalid max frame bytes: $maxFrameBytes")
  require(connectionTimeout > 0.seconds, "The connection timeout cannot be zero")
}
