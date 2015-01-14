package coinffeine.overlay.relay

import scala.concurrent.duration._

object DefaultRelaySettings {
  val ConnectionTimeout = 5.seconds
  val MaxFrameBytes = 8 * 1024 * 1024
  val IdentificationTimeout = 3.seconds
  val MinTimeBetweenStatusUpdates = 1.second
}
