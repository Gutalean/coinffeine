package coinffeine.gui.setup

import scalafx.Includes._
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.ButtonType
import scalafx.stage.WindowEvent

import coinffeine.gui.scene.CoinffeineAlert
import coinffeine.gui.scene.styles.Stylesheets
import coinffeine.gui.wizard.{StepPane, Wizard}

/** Wizard to collect the initial configuration settings */
class SetupWizard private (okPaySteps: Seq[StepPane[SetupConfig]], data: SetupConfig)
  extends Wizard[SetupConfig](
    wizardTitle = "Initial setup",
    steps = new LicenseAgreementPane +: okPaySteps,
    data = data,
    additionalStyles = Seq(Stylesheets.Setup)) {

  private def onCloseAction(ev: WindowEvent): Unit = {
    val dialog = new CoinffeineAlert(AlertType.Confirmation) {
      title = "Quit Coinffeine"
      headerText = "You will exit Coinffeine. Are you sure?"
      buttonTypes = Seq(ButtonType.Yes, ButtonType.No)
    }
    dialog.showAndWait() match {
      case Some(ButtonType.Yes) => cancel()
      case _ => ev.consume()
    }
  }

  onCloseRequest = onCloseAction _
}

object SetupWizard {

  def forTechnicalPreview(walletAddress: String): SetupWizard = {
    val data = new SetupConfig
    new SetupWizard(Seq(
      new FaucetInfoStepPane(walletAddress),
      new OkPayWalletDataPane(data)
    ), data)
  }

  def default(walletAddress: String): SetupWizard = {
    val data = new SetupConfig
    new SetupWizard(Seq(
      new OkPayCredentialsStepPane(data),
      new OkPaySeedTokenRetrievalPane(data),
      new TopUpStepPane(walletAddress)
    ), data)
  }
}
