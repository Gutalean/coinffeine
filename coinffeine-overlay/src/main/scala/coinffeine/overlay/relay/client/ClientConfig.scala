package coinffeine.overlay.relay.client

import scala.concurrent.duration._

import coinffeine.overlay.relay.DefaultRelayConfig

/** Client configuration
  *
  * @constructor
  * @param host  Hostname of the relay network server
  * @param port  Port in which the server listens
  * @param connectionTimeout  Max time to wait for a successful connection
  * @param maxFrameBytes  Maximum frame size
  */
case class ClientConfig(host: String,
                        port: Int,
                        connectionTimeout: FiniteDuration = DefaultRelayConfig.ConnectionTimeout,
                        maxFrameBytes: Int = DefaultRelayConfig.MaxFrameBytes) {
  require(maxFrameBytes > 0, s"Invalid max frame bytes: $maxFrameBytes")
  require(connectionTimeout > 0.seconds, "The connection timeout cannot be zero")
}
