package coinffeine.gui.preferences

import scalafx.Includes
import scalafx.geometry.HPos
import scalafx.scene.control.{TextField, Label, Tab}
import scalafx.scene.layout.{ColumnConstraints, GridPane}

import coinffeine.peer.config.SettingsProvider

class OkPayTab(settingsProvider: SettingsProvider) extends PreferencesTab with Includes {

  private val okPaySettings = settingsProvider.okPaySettings()

  private val walletIdField = new TextField {
    id = "preferences-okpay-userid-field"
    text = okPaySettings.userAccount.getOrElse("")
  }

  private val seedTokenField = new TextField {
    id = "preferences-okpay-token-field"
    text = okPaySettings.seedToken.getOrElse("")
  }

  text = "OK Pay"

  closable = false

  content = new GridPane() {
    styleClass += "root-pane"

    columnConstraints = Seq(
      new ColumnConstraints() { halignment = HPos.Right},
      new ColumnConstraints() { halignment = HPos.Left }
    )

    add(new Label("Wallet ID"), 0, 0)
    add(walletIdField, 1, 0)
    add(new Label("Seed token"), 0, 1)
    add(seedTokenField, 1, 1)
  }

  override def close(): Unit = {
    settingsProvider.saveUserSettings(
      okPaySettings.copy(
        userAccount = Some(walletIdField.text.value),
        seedToken = Some(seedTokenField.text.value)
      ))
  }
}
