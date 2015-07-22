package coinffeine.gui.setup

import scalafx.Includes._
import scalafx.stage.WindowEvent

import coinffeine.gui.scene.styles.Stylesheets
import coinffeine.gui.wizard.{StepPane, Wizard}

/** Wizard to collect the initial configuration settings */
class SetupWizard private (
    steps: Seq[StepPane[SetupConfig]], data: SetupConfig, exitPolicy: ExitPolicy)
  extends Wizard[SetupConfig](
    steps, data, wizardTitle = "Initial setup", additionalStyles = Seq(Stylesheets.Setup)) {

  onCloseRequest = (ev: WindowEvent) => {
    if (exitPolicy.shouldClose()) cancel() else ev.consume()
  }
}

object SetupWizard {

  def default(walletAddress: String): SetupWizard = {
    val data = new SetupConfig
    val panes = Seq(
      new LicenseAgreementPane,
      new OkPayCredentialsStepPane(data, 2),
      new OkPayProfileConfiguratorPane(data, 3),
      new TopUpStepPane(walletAddress)
    )
    new SetupWizard(panes, data, ExitPolicy.Confirmed)
  }

  def okPaySetup: SetupWizard = {
    val data = new SetupConfig
    val panes = Seq(
      new OkPayCredentialsStepPane(data, 1),
      new OkPayProfileConfiguratorPane(data, 2)
    )
    new SetupWizard(panes, data, ExitPolicy.Unconfirmed)
  }
}
