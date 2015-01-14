package coinffeine.peer.config

import coinffeine.overlay.relay.settings.RelayServerSettings
import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.payment.okpay.OkPaySettings
import coinffeine.protocol.MessageGatewaySettings

trait SettingsProvider {

  /** Retrieve application-wise settings. */
  def generalSettings(): GeneralSettings

  /** Retrieve the settings of the Bitcoin network. */
  def bitcoinSettings(): BitcoinSettings

  /** Retrieve the message gateway settings. */
  def messageGatewaySettings(): MessageGatewaySettings

  /** Retrieve the relay server settings. */
  def relayServerSettings(): RelayServerSettings

  /** Retrieve the OKPay settings. */
  def okPaySettings(): OkPaySettings

  /** Save the given user settings. */
  def saveUserSettings[A : SettingsMapping](settings: A): Unit
}
