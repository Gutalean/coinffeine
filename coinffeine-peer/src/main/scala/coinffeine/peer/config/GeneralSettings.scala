package coinffeine.peer.config

import scala.concurrent.duration.FiniteDuration

import coinffeine.peer.appdata.DataVersion

/** Application-wide settings
  *
  * @constructor
  * @param licenseAccepted          Whether the license was accepted
  * @param dataVersion              Version of the application data or None for 0.8.1 and
  *                                 previous versions
  * @param serviceStartStopTimeout  Timeout when starting/stopping application services
  */
case class GeneralSettings(
  licenseAccepted: Boolean,
  dataVersion: Option[DataVersion],
  serviceStartStopTimeout: FiniteDuration)
