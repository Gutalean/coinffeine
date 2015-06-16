package coinffeine.gui.preferences

import scalafx.Includes._
import scalafx.scene.control.{Hyperlink, Button, Label, TextField}
import scalafx.scene.layout.{HBox, VBox}
import scalafx.stage.{Modality, Stage, StageStyle}

import com.typesafe.scalalogging.LazyLogging

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.SupportWidget
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.{Stylesheets, TextStyles}
import coinffeine.gui.setup.SetupWizard
import coinffeine.gui.wizard.Wizard
import coinffeine.peer.config.SettingsProvider
import coinffeine.peer.payment.okpay.{OkPayApiCredentials, OkPaySettings}

class PaymentProcessorSettingsForm(settingsProvider: SettingsProvider) extends LazyLogging {

  private val walletIdField = new TextField
  private val seedTokenField = new TextField

  private val credentials =
    walletIdField.text.delegate.zip(seedTokenField.text) { (walletId, seedToken) =>
      OkPayApiCredentials(
        walletId = if (validWalletId(walletId)) walletId.trim else "",
        seedToken = seedToken.trim
      )
    }

  private val header = new HBox {
    styleClass += "header"
    children = Seq(
      new Label("This is your "),
      new Label("OKPay account") with TextStyles.Emphasis)
  }

  private val description = new VBox {
    styleClass += "description"
    private val commonLine = new Label("Please fill in your OKPay account details.")
    children =
      if(settingsProvider.generalSettings().techPreview) Seq(commonLine)
      else Seq(
        commonLine,
        new HBox(
          new Label("You can configure it manually"),
          new SupportWidget("manual-okpay"),
          new Label("or also")
        ),
        new Hyperlink("rerun the setup wizard") { onAction = rerunWizard _ }
      )
  }

  private val formScene = new CoinffeineScene(Stylesheets.Preferences) {
    root = new VBox() {
      styleClass += "payment-processor-preferences"
      children = Seq(
        header,
        description,
        labeledField(walletIdField, "Account ID"),
        labeledField(seedTokenField, "Token"),
        new HBox {
          styleClass += "footer"
          disable <== walletIdField.text.delegate.mapToBool(text => !validWalletId(text))
          children = new Button("Apply") {
            styleClass += "action-button"
            onAction = applyAndClose _
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

  private def applyAndClose(): Unit = {
    saveCredentials(credentials.value)
    formStage.close()
  }

  private def rerunWizard(): Unit = {
    try {
      val result = SetupWizard.okPaySetup.run(Some(formScene.window.value))
      result.okPayWalletAccess.value.foreach(saveCredentials)
    } catch {
      case _: Wizard.CancelledByUser => logger.info("OKPay wizard was cancelled by the user")
    }
    formStage.close()
  }

  private def saveCredentials(credentials: OkPayApiCredentials): Unit = {
    settingsProvider.saveUserSettings(
      settingsProvider.okPaySettings().withApiCredentials(credentials))
  }

  private def validWalletId(walletId: String): Boolean =
    walletId.trim.matches(OkPaySettings.AccountIdPattern)

  def show(): Unit = {
    val credentials = settingsProvider.okPaySettings().apiCredentials
    walletIdField.text = credentials.fold("")(_.walletId)
    seedTokenField.text = credentials.fold("")(_.seedToken)
    walletIdField.requestFocus()
    formStage.showAndWait()
  }
}
