package coinffeine.peer.config

import coinffeine.common.properties.Property
import coinffeine.overlay.relay.settings.RelaySettings
import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.payment.okpay.OkPaySettings
import coinffeine.protocol.MessageGatewaySettings

trait SettingsProvider {

  def generalSettingsProperty: Property[GeneralSettings]
  def bitcoinSettingsProperty: Property[BitcoinSettings]
  def messageGatewaySettingsProperty: Property[MessageGatewaySettings]
  def relaySettingsProperty: Property[RelaySettings]
  def okPaySettingsProperty: Property[OkPaySettings]

  def generalSettings(): GeneralSettings = generalSettingsProperty.get
  def bitcoinSettings(): BitcoinSettings = bitcoinSettingsProperty.get
  def messageGatewaySettings(): MessageGatewaySettings = messageGatewaySettingsProperty.get
  def relaySettings(): RelaySettings = relaySettingsProperty.get
  def okPaySettings(): OkPaySettings = okPaySettingsProperty.get

  def saveUserSettings[A : SettingsMapping](settings: A): Unit
}
