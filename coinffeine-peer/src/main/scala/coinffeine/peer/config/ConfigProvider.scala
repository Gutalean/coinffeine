package coinffeine.peer.config

import com.typesafe.config.{ConfigFactory, Config}

import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.payment.okpay.OkPaySettings
import coinffeine.protocol.MessageGatewaySettings

trait ConfigProvider extends SettingsProvider {

  /** Retrieve the user configuration. */
  def userConfig: Config

  /** Retrieve the reference configuration obtained from the app bundle. */
  def referenceConfig: Config = ConfigFactory.load()

  /** Retrieve the whole configuration, including reference and user config. */
  def config: Config = userConfig.withFallback(referenceConfig)

  override lazy val bitcoinSettings = BitcoinSettings(config)
  override lazy val messageGatewaySettings = MessageGatewaySettings(config)
  override lazy val okPaySettings = OkPaySettings(config)
}
