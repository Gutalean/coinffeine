package coinffeine.peer.config

import com.typesafe.config.{ConfigValueFactory, ConfigFactory, Config}

import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.config.user.LocalAppDataDir
import coinffeine.peer.payment.okpay.OkPaySettings
import coinffeine.protocol.MessageGatewaySettings

trait ConfigProvider extends SettingsProvider {

  import SettingsMapping._

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
  def referenceConfig: Config = ConfigFactory.load().withValue(
    "coinffeine.bitcoin.walletFile",
    ConfigValueFactory.fromAnyRef(
      LocalAppDataDir.getFile("user.wallet", ensureCreated = false).toAbsolutePath.toString))

  /** Retrieve the whole configuration, including reference and user config. */
  def config: Config = userConfig.withFallback(referenceConfig)

  override lazy val bitcoinSettings =
    SettingsMapping.fromConfig[BitcoinSettings](config)

  override lazy val messageGatewaySettings =
    SettingsMapping.fromConfig[MessageGatewaySettings](config)

  override lazy val okPaySettings =
    SettingsMapping.fromConfig[OkPaySettings](config)

  /** Save the given settings as part of user config using this provider.
    *
    * This function maps the settings to a config object that is then merged with the current
    * user configuration before being persisted.
    */
  override def saveUserSettings[A : SettingsMapping](settings: A): Unit =
    saveUserConfig(SettingsMapping.toConfig(settings, userConfig), dropReferenceValues = true)
}
