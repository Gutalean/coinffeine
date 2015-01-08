package coinffeine.overlay.relay

import scala.concurrent.duration._

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
                        connectionTimeout: FiniteDuration = ClientConfig.DefaultConnectionTimeout,
                        maxFrameBytes: Int = ClientConfig.DefaultMaxFrameBytes) {
  require(maxFrameBytes > 0, s"Invalid max frame bytes: $maxFrameBytes")
  require(connectionTimeout > 0.seconds, "The connection timeout cannot be zero")
}

object ClientConfig {
  val DefaultConnectionTimeout = 5.seconds
  val DefaultMaxFrameBytes = 8 * 1024 * 1024
}
