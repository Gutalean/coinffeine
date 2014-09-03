package coinffeine.peer.config

import com.typesafe.config.{Config, ConfigFactory}

trait SettingsProvider {

  /** Retrieve the user configuration. */
  def userConfig: Config

  /** Retrieve the reference configuration obtained from the app bundle. */
  def referenceConfig: Config = ConfigFactory.load()

  /** Retrieve the whole configuration, including reference and user config. */
  def config: Config = userConfig.withFallback(referenceConfig)
}
