package coinffeine.gui.application.launcher

import java.io.File
import scala.concurrent.Future

import com.typesafe.scalalogging.StrictLogging
import org.bitcoinj.core.Wallet

import coinffeine.gui.setup.{SetupConfig, SetupWizard}
import coinffeine.gui.util.FxExecutor
import coinffeine.model.bitcoin.Network
import coinffeine.peer.bitcoin.wallet.SmartWallet
import coinffeine.peer.config.ConfigProvider

class RunWizardAction(configProvider: ConfigProvider, network: Network) extends StrictLogging {

  def apply(): Future[Unit] = Future(if (mustRunWizard) { runSetupWizard() })(FxExecutor.asContext)

  private def mustRunWizard: Boolean = !configProvider.generalSettings().licenseAccepted

  private def runSetupWizard(): Unit = {
    val topUpAddress = loadOrCreateWallet().delegate.freshReceiveAddress()
    persistSetupConfiguration(new SetupWizard(topUpAddress.toString).run())
  }

  private def loadOrCreateWallet(): SmartWallet = {
    val walletFile = configProvider.bitcoinSettings().walletFile
    if (!walletFile.isFile) {
      createWallet(walletFile)
    }
    SmartWallet.loadFromFile(walletFile)
  }

  private def createWallet(walletFile: File): Unit = {
    new Wallet(network).saveToFile(walletFile)
    logger.info("Created new wallet at {}", walletFile)
  }

  private def persistSetupConfiguration(setupConfig: SetupConfig): Unit = {
    configProvider.saveUserSettings(
      configProvider.generalSettings().copy(licenseAccepted = true))

    setupConfig.okPayWalletAccess.foreach { access =>
      val okPaySettings = configProvider.okPaySettings()
      configProvider.saveUserSettings(okPaySettings.copy(
        userAccount = Some(access.walletId),
        seedToken = Some(access.seedToken)
      ))
    }
  }
}
