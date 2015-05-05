package coinffeine.peer.config

import scala.concurrent.duration.FiniteDuration

/** Application-wide settings
  *
  * @constructor
  * @param licenseAccepted          Whether the license was accepted
  * @param serviceStartStopTimeout  Timeout when starting/stopping application services
  */
case class GeneralSettings(licenseAccepted: Boolean,
                           serviceStartStopTimeout: FiniteDuration)
