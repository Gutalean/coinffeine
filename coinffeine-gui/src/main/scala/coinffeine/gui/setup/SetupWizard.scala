package coinffeine.gui.setup

import scalafx.Includes._
import scalafx.stage.WindowEvent

import org.controlsfx.dialog.{DialogStyle, Dialog, Dialogs}

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
    val answer = Dialogs.create()
      .style(DialogStyle.NATIVE)
      .title("Quit Coinffeine")
      .message("You will exit Coinffeine. Are you sure?")
      .actions(Dialog.Actions.NO, Dialog.Actions.YES)
      .showConfirm()
    answer match {
      case Dialog.Actions.YES => cancel()
      case _ => ev.consume()
    }
  }

  onCloseRequest = { ev: WindowEvent => onCloseAction(ev) }
}
