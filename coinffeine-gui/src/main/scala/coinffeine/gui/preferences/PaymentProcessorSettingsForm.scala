package coinffeine.gui.preferences

import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.scene.control.{Button, Label, TextField}
import scalafx.scene.layout.{HBox, VBox}
import scalafx.stage.{Modality, Stage, StageStyle}

import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.{Stylesheets, TextStyles}
import coinffeine.peer.config.SettingsProvider

class PaymentProcessorSettingsForm(settingsProvider: SettingsProvider) {

  private val okPaySettings = settingsProvider.okPaySettings()

  private val accountIdField = new TextField {
    text = okPaySettings.userAccount.getOrElse("")
  }

  private val tokenField = new TextField {
    text = okPaySettings.seedToken.getOrElse("")
  }

  private val formScene = new CoinffeineScene(Stylesheets.Preferences) {
    root = new VBox() {
      styleClass += "payment-processor-preferences"
      content = Seq(
        new HBox {
          styleClass += "header"
          content = Seq(
            new Label("This is your "),
            new Label("OKPay account") with TextStyles.Emphasis)
        },
        new Label("Please fill in your OKPay account details"),
        labeledField(accountIdField, "Account ID"),
        labeledField(tokenField, "Token"),
        new HBox {
          styleClass += "footer"
          content = new Button("Apply") {
            styleClass += "action-button"
            onAction = { e: ActionEvent => close() }
          }
        }
      )
    }
  }

  private def labeledField(field: TextField, label: String) = new VBox {
    styleClass += "labeled-field"
    content = Seq(
      new Label(label) with TextStyles.Emphasis,
      field
    )
  }

  private val formStage = new Stage(style = StageStyle.UTILITY) {
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
        userAccount = Some(accountIdField.text.value),
        seedToken = Some(tokenField.text.value)
      ))
  }

  def show(): Unit = {
    formStage.showAndWait()
  }
}
