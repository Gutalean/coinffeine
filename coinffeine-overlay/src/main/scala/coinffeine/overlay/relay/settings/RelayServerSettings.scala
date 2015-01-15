package coinffeine.overlay.relay.settings

import scala.concurrent.duration.FiniteDuration

import coinffeine.common.DurationUtils
import coinffeine.overlay.relay.DefaultRelaySettings

/** Relay server configuration.
  *
  * @constructor
  * @param bindAddress  Address to bind to. Use 0.0.0.0 to bind all interfaces
  * @param bindPort     Port to bind to
  * @param maxFrameBytes  Maximum frame size
  * @param identificationTimeout  Timeout to kick a client that has not identify itself with a
  *                               join message
  * @param minTimeBetweenStatusUpdates  Clients won't receive status updates in less time than
  *                                     this to limit its rate
  */
case class RelayServerSettings(
    bindAddress: String,
    bindPort: Int,
    maxFrameBytes: Int = DefaultRelaySettings.MaxFrameBytes,
    identificationTimeout: FiniteDuration = DefaultRelaySettings.IdentificationTimeout,
    minTimeBetweenStatusUpdates: FiniteDuration = DefaultRelaySettings.MinTimeBetweenStatusUpdates) {
  DurationUtils.requirePositive(identificationTimeout, "The identification timeout")
}
