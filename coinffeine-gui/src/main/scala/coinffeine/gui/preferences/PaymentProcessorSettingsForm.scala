package coinffeine.gui.preferences

import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.geometry.HPos
import scalafx.scene.control.{Button, Label, TextField}
import scalafx.scene.layout.{ColumnConstraints, GridPane, VBox}
import scalafx.stage.{Modality, Stage, StageStyle}

import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.Stylesheets
import coinffeine.peer.config.SettingsProvider

class PaymentProcessorSettingsForm(settingsProvider: SettingsProvider) {

  private val okPaySettings = settingsProvider.okPaySettings()

  private val walletIdField = new TextField {
    id = "preferences-okpay-userid-field"
    text = okPaySettings.userAccount.getOrElse("")
  }

  private val seedTokenField = new TextField {
    id = "preferences-okpay-token-field"
    text = okPaySettings.seedToken.getOrElse("")
  }

  private val formScene = new CoinffeineScene(Stylesheets.Preferences) {
    root = new VBox() {
      id = "preferences-root-pane"
      content = Seq(
        new VBox() {
          id = "okpay-tab"
          content = new GridPane() {
            id = "preferences-okpay-details"

            columnConstraints = Seq(
              new ColumnConstraints() {
                halignment = HPos.Right
              },
              new ColumnConstraints() {
                halignment = HPos.Left
              }
            )

            add(new Label("Wallet ID"), 0, 0)
            add(walletIdField, 1, 0)
            add(new Label("Seed token"), 0, 1)
            add(seedTokenField, 1, 1)
          }
        },
        new Button("Apply") {
          onAction = { e: ActionEvent => close() }
        }
      )
    }
  }

  private val formStage = new Stage(style = StageStyle.DECORATED) {
    title = "Payment processor settings"
    resizable = false
    initModality(Modality.APPLICATION_MODAL)
    scene = formScene
    centerOnScreen()
  }

  private def close(): Unit = {
    saveSettings()
    formStage.close()
  }

  private def saveSettings(): Unit = {
    settingsProvider.saveUserSettings(
      okPaySettings.copy(
        userAccount = Some(walletIdField.text.value),
        seedToken = Some(seedTokenField.text.value)
      ))
  }

  def show(): Unit = {
    formStage.showAndWait()
  }
}
