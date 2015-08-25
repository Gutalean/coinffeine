package coinffeine.peer.config

import java.io.File
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global

import com.typesafe.config.{Config, ConfigFactory}

import coinffeine.common.properties.{Property, MutableProperty}
import coinffeine.overlay.relay.settings.RelaySettings
import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.payment.okpay.OkPaySettings
import coinffeine.protocol.MessageGatewaySettings

/** Provide application settings based on a Typesafe Config source */
class ConfigProvider(store: ConfigStore) extends SettingsProvider {

  private lazy val _userConfig: MutableProperty[Config] = new MutableProperty(store.readConfig())

  override lazy val generalSettingsProperty = bindSettings[GeneralSettings]
  override lazy val bitcoinSettingsProperty = bindSettings[BitcoinSettings]
  override lazy val messageGatewaySettingsProperty = bindSettings[MessageGatewaySettings]
  override lazy val relaySettingsProperty = bindSettings[RelaySettings]
  override lazy val okPaySettingsProperty = bindSettings[OkPaySettings]

  /** Retrieve the raw user configuration. */
  def userConfig: Config = _userConfig.get

  /** Get the application configuration path. */
  def dataPath: File = store.dataPath

  /** Save the given user config using this provider.
    *
    * This function will save the given user config, **replacing the previous one** if any.
    * The `dropReferenceValues` flag indicates whether those config items that does not
    * override the reference config must be drop away so they are not included in user config.
    */
  def saveUserConfig(userConfig: Config, dropReferenceValues: Boolean = true): Unit = {
    val config = if (dropReferenceValues) diff(userConfig, referenceConfig) else userConfig
    store.writeConfig(config)
    _userConfig.set(config)
  }

  /** Retrieve the reference configuration obtained from the app bundle. */
  def referenceConfig: Config = defaultPathConfiguration.withFallback(ConfigFactory.load())

  private def defaultPathConfiguration = ConfigFactory.parseMap(Map(
    "akka.persistence.journal.leveldb.dir" -> configPath("journal"),
    "akka.persistence.snapshot-store.local.dir" -> configPath("snapshots")
  ).asJava)

  private def configPath(filename: String) = new File(dataPath, filename).toString

  /** Retrieve the whole configuration, including reference and user config. */
  def enrichedConfig: Config = enrichConfig(userConfig)

  private def enrichConfig(config: Config): Config = config.withFallback(referenceConfig)

  /** Save the given settings as part of user config using this provider.
    *
    * This function maps the settings to a config object that is then merged with the current
    * user configuration before being persisted.
    */
  override def saveUserSettings[A : SettingsMapping](settings: A): Unit =
    saveUserConfig(SettingsMapping.toConfig(settings, userConfig), dropReferenceValues = true)


  private def bindSettings[A: SettingsMapping]: Property[A] = {
    def map(config: Config) = SettingsMapping.fromConfig[A](dataPath, enrichConfig(config))
    val prop = new MutableProperty(map(userConfig))
    _userConfig.onNewValue(config => prop.set(map(config)))
    prop
  }

  private def diff(c1: Config, c2: Config): Config = {
    val c1Items = c1.entrySet().asScala.map(entry => entry.getKey -> entry.getValue)
    val c2Items = c2.entrySet().asScala.map(entry => entry.getKey -> entry.getValue)
    val c3Items = c1Items.diff(c2Items)
    c3Items.foldLeft(ConfigFactory.empty()) { case (conf, (key, value)) =>
      conf.withValue(key, value)
    }
  }
}
