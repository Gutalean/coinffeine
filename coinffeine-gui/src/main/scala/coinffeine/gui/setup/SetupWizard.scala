package coinffeine.gui.setup

import scalafx.Includes._
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.ButtonType
import scalafx.stage.WindowEvent

import coinffeine.gui.scene.CoinffeineAlert
import coinffeine.gui.scene.styles.Stylesheets
import coinffeine.gui.wizard.Wizard

/** Wizard to collect the initial configuration settings */
class SetupWizard(walletAddress: String,
                  data: SetupConfig = new SetupConfig) extends Wizard[SetupConfig](
    wizardTitle = "Initial setup",
    steps = Seq(
      new LicenseAgreementPane,
      new FaucetInfoStepPane(walletAddress),
      new OkPayWalletDataPane(data)
    ),
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
