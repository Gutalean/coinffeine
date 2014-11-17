package coinffeine.gui.preferences

import scalafx.beans.property.{IntegerProperty, BooleanProperty}
import scalafx.scene.control.{ToggleGroup, TextField, Label, RadioButton}
import scalafx.scene.layout.{HBox, VBox}

import coinffeine.gui.util.ScalafxImplicits._
import coinffeine.peer.config.SettingsProvider

class NetworkTab(settingsProvider: SettingsProvider) extends PreferencesTab {

  override lazy val tabTitle = "Network"

  private val initialSettings = settingsProvider.messageGatewaySettings()

  private val isAutomatic = new BooleanProperty(this, "isAutomatic",
    !initialSettings.externalForwardedPort.isDefined)

  private val forwardedPorts = new IntegerProperty(this, "forwardedPorts",
    initialSettings.externalForwardedPort.getOrElse(NetworkTab.DefaultForwardedNetworkPorts))

  private val networkModeToggleGroup = new ToggleGroup()

  private val automaticRadioButton = new RadioButton("Autodetect connection settings (recommended)") {
    toggleGroup = networkModeToggleGroup
  }

  private val manualRadioButton = new RadioButton("Manual port forwarding") {
    toggleGroup = networkModeToggleGroup
  }

  networkModeToggleGroup.selectToggle(
    if (isAutomatic.value) automaticRadioButton.delegate else manualRadioButton.delegate)

  isAutomatic <== networkModeToggleGroup.delegate.selectedToggleProperty()
    .isEqualTo(automaticRadioButton.delegate)

  private val automaticWrapper = new VBox() {
    styleClass += "network-option"
    content = Seq(
      automaticRadioButton,
      new VBox() {
        styleClass += "network-option-details"
        content = Seq(
          new Label("Coinffeine will use uPnP or NAT-PMP to configure your ports automatically."),
          new Label("You must ensure uPnP or NAT-PMP are enabled in your router or network.")
        )
      }
    )
  }

  private val manualWrapper = new VBox() {
    styleClass += "network-option"
    content = Seq(
      manualRadioButton,
      new VBox() {
        styleClass += "network-option-details"
        content = Seq(
          new Label("Coinffeine will autodetect your IP address, " +
            "but you have to configure your ports manually."),
          new HBox() {
            content = Seq(
              new Label("The TCP & UDP ports"),
              new TextField() {
                styleClass += "network-port"
                text = forwardedPorts.value.toString
                forwardedPorts <== text.delegate.mapToInt(_.toInt)
                disable <== isAutomatic
              },
              new Label("are redirected to this computer.")
            )
          }
        )
      }
    )
  }

  content = new VBox() {
    id = "network-tab"
    content = Seq(
      new Label("Please select how Coinffeine is connected to the Internet."),
      automaticWrapper,
      manualWrapper
    )
  }

  override def apply() = {
    val forwardedPort = if (isAutomatic.value) None else Some(forwardedPorts.value)
    val newSettings = initialSettings.copy(externalForwardedPort = forwardedPort)
    if (initialSettings != newSettings) {
      settingsProvider.saveUserSettings(newSettings)
    }
  }
}

object NetworkTab {

  private val DefaultForwardedNetworkPorts = 5460
}
