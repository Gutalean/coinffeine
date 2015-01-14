package coinffeine.peer.config

import scala.collection.JavaConverters._

import com.typesafe.config.{Config, ConfigFactory}
import coinffeine.overlay.relay.settings.RelayServerSettings
import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.config.user.LocalAppDataDir
import coinffeine.peer.payment.okpay.OkPaySettings
import coinffeine.protocol.MessageGatewaySettings

trait ConfigProvider extends SettingsProvider {

  /** Retrieve the user configuration. */
  def userConfig: Config

  /** Save the given user config using this provider.
    *
    * This function will save the given user config, **replacing the previous one** if any.
    * The `dropReferenceValues` flag indicates whether those config items that does not
    * override the reference config must be drop away so they are not included in user config.
    */
  def saveUserConfig(userConfig: Config, dropReferenceValues: Boolean = true): Unit

  /** Retrieve the reference configuration obtained from the app bundle. */
  def referenceConfig: Config = defaultPathConfiguration.withFallback(ConfigFactory.load())

  private def defaultPathConfiguration = ConfigFactory.parseMap(Map(
    "coinffeine.bitcoin.walletFile" -> configPath("user.wallet"),
    "akka.persistence.journal.leveldb.dir" -> configPath("journal"),
    "akka.persistence.snapshot-store.local.dir" -> configPath("snapshots")
  ).asJava)

  private def configPath(filename: String) =
    LocalAppDataDir.getFile(filename, ensureCreated = false).toAbsolutePath.toString

  /** Retrieve the whole configuration, including reference and user config. */
  def config: Config = userConfig.withFallback(referenceConfig)

  override def generalSettings() = SettingsMapping.fromConfig[GeneralSettings](config)

  override def bitcoinSettings() = SettingsMapping.fromConfig[BitcoinSettings](config)

  override def messageGatewaySettings() = {
    ensurePeerIdIsDefined()
    SettingsMapping.fromConfig[MessageGatewaySettings](config)
  }

  override def relayServerSettings() = SettingsMapping.fromConfig[RelayServerSettings](config)

  private def ensurePeerIdIsDefined(): Unit = {
    SettingsMapping.MessageGateway.ensurePeerId(config).foreach(saveUserConfig(_))
  }

  override def okPaySettings() = SettingsMapping.fromConfig[OkPaySettings](config)

  /** Save the given settings as part of user config using this provider.
    *
    * This function maps the settings to a config object that is then merged with the current
    * user configuration before being persisted.
    */
  override def saveUserSettings[A : SettingsMapping](settings: A): Unit =
    saveUserConfig(SettingsMapping.toConfig(settings, userConfig), dropReferenceValues = true)
}
