package coinffeine.gui.preferences

import scalafx.geometry.HPos
import scalafx.scene.control.{TextField, Label, Tab}
import scalafx.scene.layout.{ColumnConstraints, GridPane}

import coinffeine.peer.config.SettingsProvider

class OkPayTab(settingsProvider: SettingsProvider) extends Tab {

  private val okPaySettings = settingsProvider.okPaySettings()

  text = "OK Pay"

  closable = false

  content = new GridPane() {
    styleClass += "root-pane"

    columnConstraints = Seq(
      new ColumnConstraints() { halignment = HPos.Right},
      new ColumnConstraints() { halignment = HPos.Left }
    )

    add(new Label("Wallet ID"), 0, 0)
    add(new TextField {
      id = "preferences-okpay-userid-field"
      text = okPaySettings.userAccount.getOrElse("")
    }, 1, 0)
    add(new Label("Seed token"), 0, 1)
    add(new TextField {
      id = "preferences-okpay-token-field"
      text = okPaySettings.seedToken.getOrElse("")
    }, 1, 1)
  }
}
