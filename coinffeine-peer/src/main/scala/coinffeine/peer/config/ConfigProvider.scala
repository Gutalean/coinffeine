package coinffeine.peer.config

import java.io.File
import scala.collection.JavaConverters._

import com.typesafe.config.{Config, ConfigFactory}

import coinffeine.common.properties.MutableProperty
import coinffeine.overlay.relay.settings.RelaySettings
import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.payment.okpay.OkPaySettings
import coinffeine.protocol.MessageGatewaySettings

/** Provide application settings based on a Typesafe Config source */
trait ConfigProvider extends SettingsProvider {

  private lazy val _userConfig: MutableProperty[Config] = new MutableProperty(readConfig())

  /** Retrieve the raw user configuration. */
  def userConfig: Config = _userConfig.get

  /** Get the application configuration path. */
  def dataPath: File

  /** Save the given user config using this provider.
    *
    * This function will save the given user config, **replacing the previous one** if any.
    * The `dropReferenceValues` flag indicates whether those config items that does not
    * override the reference config must be drop away so they are not included in user config.
    */
  def saveUserConfig(userConfig: Config, dropReferenceValues: Boolean = true): Unit = {
    val config = if (dropReferenceValues) diff(userConfig, referenceConfig) else userConfig
    writeConfig(config)
    _userConfig.set(config)
  }

  /** Retrieve the reference configuration obtained from the app bundle. */
  def referenceConfig: Config = defaultPathConfiguration.withFallback(ConfigFactory.load())

  protected def readConfig(): Config

  protected def writeConfig(config: Config): Unit

  private def defaultPathConfiguration = ConfigFactory.parseMap(Map(
    "akka.persistence.journal.leveldb.dir" -> configPath("journal"),
    "akka.persistence.snapshot-store.local.dir" -> configPath("snapshots")
  ).asJava)

  private def configPath(filename: String) = new File(dataPath, filename).toString

  /** Retrieve the whole configuration, including reference and user config. */
  def enrichedConfig: Config = userConfig.withFallback(referenceConfig)

  override def generalSettings() =
    SettingsMapping.fromConfig[GeneralSettings](dataPath, enrichedConfig)

  override def bitcoinSettings() =
    SettingsMapping.fromConfig[BitcoinSettings](dataPath, enrichedConfig)

  override def messageGatewaySettings() = {
    ensurePeerIdIsDefined()
    SettingsMapping.fromConfig[MessageGatewaySettings](dataPath, enrichedConfig)
  }

  override def relaySettings() =
    SettingsMapping.fromConfig[RelaySettings](dataPath, enrichedConfig)

  private def ensurePeerIdIsDefined(): Unit = {
    SettingsMapping.MessageGateway.ensurePeerId(enrichedConfig).foreach(saveUserConfig(_))
  }

  override def okPaySettings() =
    SettingsMapping.fromConfig[OkPaySettings](dataPath, enrichedConfig)

  /** Save the given settings as part of user config using this provider.
    *
    * This function maps the settings to a config object that is then merged with the current
    * user configuration before being persisted.
    */
  override def saveUserSettings[A : SettingsMapping](settings: A): Unit =
    saveUserConfig(SettingsMapping.toConfig(settings, userConfig), dropReferenceValues = true)


  private def diff(c1: Config, c2: Config): Config = {
    val c1Items = c1.entrySet().asScala.map(entry => entry.getKey -> entry.getValue)
    val c2Items = c2.entrySet().asScala.map(entry => entry.getKey -> entry.getValue)
    val c3Items = c1Items.diff(c2Items)
    c3Items.foldLeft(ConfigFactory.empty()) { case (conf, (key, value)) =>
      conf.withValue(key, value)
    }
  }
}
