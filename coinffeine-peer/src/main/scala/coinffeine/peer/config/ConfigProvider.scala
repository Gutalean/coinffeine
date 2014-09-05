package coinffeine.peer.config

import com.typesafe.config.{ConfigFactory, Config}

import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.payment.okpay.OkPaySettings
import coinffeine.protocol.MessageGatewaySettings

trait ConfigProvider extends SettingsProvider {

  import SettingsMapping._

  /** Retrieve the user configuration. */
  def userConfig: Config

  /** Save the given user config using this provider. */
  def saveUserConfig(userConfig: Config, dropReferenceValues: Boolean = true): Unit

  /** Retrieve the reference configuration obtained from the app bundle. */
  def referenceConfig: Config = ConfigFactory.load()

  /** Retrieve the whole configuration, including reference and user config. */
  def config: Config = userConfig.withFallback(referenceConfig)

  override lazy val bitcoinSettings =
    SettingsMapping.fromConfig[BitcoinSettings](config)

  override lazy val messageGatewaySettings =
    SettingsMapping.fromConfig[MessageGatewaySettings](config)

  override lazy val okPaySettings =
    SettingsMapping.fromConfig[OkPaySettings](config)
}
