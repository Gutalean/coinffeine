package coinffeine.gui.preferences

import scalafx.Includes._
import scalafx.scene.control.{Button, Label, TextField}
import scalafx.scene.layout.{HBox, VBox}
import scalafx.stage.{Modality, Stage, StageStyle}

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.{Stylesheets, TextStyles}
import coinffeine.peer.config.SettingsProvider
import coinffeine.peer.payment.okpay.{OkPayApiCredentials, OkPaySettings}

class PaymentProcessorSettingsForm(settingsProvider: SettingsProvider) {

  private val walletIdField = new TextField
  private val seedTokenField = new TextField

  private val credentials =
    walletIdField.text.delegate.zip(seedTokenField.text) { (walletId, seedToken) =>
      OkPayApiCredentials(
        walletId = if (validWalletId(walletId)) walletId.trim else "",
        seedToken = seedToken.trim
      )
    }

  private val formScene = new CoinffeineScene(Stylesheets.Preferences) {
    root = new VBox() {
      styleClass += "payment-processor-preferences"
      children = Seq(
        new HBox {
          styleClass += "header"
          children = Seq(
            new Label("This is your "),
            new Label("OKPay account") with TextStyles.Emphasis)
        },
        new Label("Please fill in your OKPay account details"),
        labeledField(walletIdField, "Account ID"),
        labeledField(seedTokenField, "Token"),
        new HBox {
          styleClass += "footer"
          disable <== walletIdField.text.delegate.mapToBool(text => !validWalletId(text))
          children = new Button("Apply") {
            styleClass += "action-button"
            onAction = () => close()
          }
        }
      )
    }
  }

  private def labeledField(field: TextField, label: String) = new VBox {
    styleClass += "labeled-field"
    children = Seq(
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
    saveCredentials()
    formStage.close()
  }

  private def saveCredentials(): Unit = {
    settingsProvider.saveUserSettings(
      settingsProvider.okPaySettings().withApiCredentials(credentials.value))
  }

  private def validWalletId(walletId: String): Boolean =
    walletId.trim.matches(OkPaySettings.AccountIdPattern)

  def show(): Unit = {
    val credentials = settingsProvider.okPaySettings().apiCredentials
    walletIdField.text = credentials.fold("")(_.walletId)
    seedTokenField.text = credentials.fold("")(_.seedToken)
    formStage.showAndWait()
  }
}
