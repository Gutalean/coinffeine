package coinffeine.gui.preferences

import scalafx.Includes._
import scalafx.scene.Node
import scalafx.scene.control._
import scalafx.scene.layout.{HBox, VBox}
import scalafx.stage.{Modality, Stage, StageStyle}

import com.typesafe.scalalogging.LazyLogging

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{CredentialsTestWidget, SupportWidget}
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.{Stylesheets, TextStyles}
import coinffeine.gui.setup.SetupWizard
import coinffeine.gui.wizard.Wizard
import coinffeine.model.payment.okpay.VerificationStatus
import coinffeine.peer.api.CoinffeinePaymentProcessor
import coinffeine.peer.config.SettingsProvider
import coinffeine.peer.payment.okpay.{OkPayApiCredentials, OkPaySettings}

class PaymentProcessorSettingsDialog(
    settingsProvider: SettingsProvider,
    paymentProcessor: CoinffeinePaymentProcessor) extends LazyLogging {

  private val walletIdField = new TextField
  private val seedTokenField = new TextField
  private val verificationStatusField = new CheckBox("Verified account")

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
    children = Seq(
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
        labelledField("Account ID", walletIdField),
        labelledField("Token", seedTokenField),
        new CredentialsTestWidget(paymentProcessor) {
          credentials <== PaymentProcessorSettingsDialog.this.credentials
        },
        field(
          new HBox(verificationStatusField, new SupportWidget("setup-verification")),
          new Label("Unverified accounts cannot transfer more than 300EUR/month") {
            styleClass += "explanation"
          }
        ),
        new HBox {
          styleClass += "footer"
          disable <== walletIdField.text.delegate.map(text => !validWalletId(text)).toBool
          children = new Button("Apply") {
            styleClass += "action-button"
            onAction = applyAndClose _
          }
        }
      )
    }
  }

  private def labelledField(label: String, node: Node) =
    field(new Label(label) with TextStyles.Emphasis, node)

  private def field(nodes: Node*) = new VBox {
    styleClass += "field"
    children = nodes
  }

  private val formStage = new Stage(style = StageStyle.UTILITY) {
    title = "Payment processor settings"
    resizable = false
    initModality(Modality.APPLICATION_MODAL)
    scene = formScene
    centerOnScreen()
  }

  private def applyAndClose(): Unit = {
    val verificationStatus =
      VerificationStatus.fromBoolean(verificationStatusField.selected.value)
    saveSettings(credentials.value, verificationStatus)
    paymentProcessor.refreshBalances()
    formStage.close()
  }

  private def rerunWizard(): Unit = {
    try {
      val result = SetupWizard.okPaySetup.run(Some(formScene.window.value))
      val credentials = result.okPayWalletAccess.value.getOrElse(OkPayApiCredentials.empty)
      val verificationStatus =
        result.okPayVerificationStatus.value.getOrElse(VerificationStatus.NotVerified)
      saveSettings(credentials, verificationStatus)
    } catch {
      case _: Wizard.CancelledByUser => logger.info("OKPay wizard was cancelled by the user")
    }
    formStage.close()
  }

  private def saveSettings(
      credentials: OkPayApiCredentials, verificationStatus: VerificationStatus): Unit = {
    settingsProvider.saveUserSettings(
      settingsProvider.okPaySettings()
          .withApiCredentials(credentials)
          .copy(verificationStatus = Some(verificationStatus)))
  }

  private def validWalletId(walletId: String): Boolean =
    walletId.trim.matches(OkPaySettings.AccountIdPattern)

  def show(): Unit = {
    resetFieldsToCurrentSettings()
    walletIdField.requestFocus()
    formStage.showAndWait()
  }

  private def resetFieldsToCurrentSettings(): Unit = {
    val currentSettings = settingsProvider.okPaySettings()
    resetCredentialFields(
      currentSettings.apiCredentials.getOrElse(OkPayApiCredentials.empty))
    resetVerificationStatusField(
      currentSettings.verificationStatus.getOrElse(VerificationStatus.NotVerified))
  }

  private def resetCredentialFields(credentials: OkPayApiCredentials): Unit = {
    walletIdField.text = credentials.walletId
    seedTokenField.text = credentials.seedToken
  }

  private def resetVerificationStatusField(verificationStatus: VerificationStatus): Unit = {
    verificationStatusField.selected = verificationStatus == VerificationStatus.Verified
  }
}
