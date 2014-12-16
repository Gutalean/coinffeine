package coinffeine.gui.setup

import coinffeine.gui.wizard.Wizard

/** Wizard to collect the initial configuration settings */
class SetupWizard(walletAddress: String) extends Wizard[SetupConfig](
  wizardTitle = "Initial setup",
  steps = Seq(
    new LicenseAgreementPane,
    new OkPayWalletDataPane,
    new TopUpStepPane(walletAddress)
  ),
  initialData = SetupConfig(password = None, okPayCredentials = None, okPayWalletAccess = None)
)
