package coinffeine.gui.application.launcher

import java.io.File
import scala.util.{Success, Try}

import com.typesafe.scalalogging.StrictLogging
import org.bitcoinj.core.Wallet

import coinffeine.gui.setup.{SetupConfig, SetupWizard}
import coinffeine.model.bitcoin.Network
import coinffeine.peer.bitcoin.wallet.SmartWallet
import coinffeine.peer.config.ConfigProvider
import coinffeine.peer.config.user.LocalAppDataDir

class RunWizardAction(configProvider: ConfigProvider, network: Network) extends StrictLogging {

  def apply(): Try[Unit] = if (mustRunWizard) { runSetupWizard() } else Success {}

  private def mustRunWizard: Boolean = !configProvider.generalSettings().licenseAccepted

  private def runSetupWizard() = Try {
    val topUpAddress = loadOrCreateWallet().delegate.freshReceiveAddress()
    persistSetupConfiguration(new SetupWizard(topUpAddress.toString).run())
  }

  private def loadOrCreateWallet(): SmartWallet = {
    if (!configProvider.bitcoinSettings().walletFile.isFile) {
      configProvider.saveUserSettings(
        configProvider.bitcoinSettings().copy(walletFile = createWallet()))
    }
    SmartWallet.loadFromFile(configProvider.bitcoinSettings().walletFile)
  }

  private def createWallet(): File = {
    val walletFile = LocalAppDataDir.getFile("user.wallet", ensureCreated = false).toFile
    new Wallet(network).saveToFile(walletFile)
    logger.info("Created new wallet at {}", walletFile)
    walletFile
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
