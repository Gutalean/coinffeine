package coinffeine.gui.preferences

import scalafx.Includes
import scalafx.geometry.HPos
import scalafx.scene.control.{TextField, Label, Tab}
import scalafx.scene.layout.{VBox, ColumnConstraints, GridPane}

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

  override lazy val tabTitle = "OK Pay"

  private val detailsPane = new GridPane() {
    id = "preferences-okpay-details"

    columnConstraints = Seq(
      new ColumnConstraints() { halignment = HPos.Right},
      new ColumnConstraints() { halignment = HPos.Left }
    )

    add(new Label("Wallet ID"), 0, 0)
    add(walletIdField, 1, 0)
    add(new Label("Seed token"), 0, 1)
    add(seedTokenField, 1, 1)
  }

  content = new VBox() {
    id = "okpay-tab"
    content = Seq(
      new Label("Please fill your OK Pay account details"),
      detailsPane
    )
  }

  override def apply(): Unit = {
    settingsProvider.saveUserSettings(
      okPaySettings.copy(
        userAccount = Some(walletIdField.text.value),
        seedToken = Some(seedTokenField.text.value)
      ))
  }
}
