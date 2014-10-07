package coinffeine.gui

import java.io.File
import scala.concurrent.Await
import scala.concurrent.duration._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.image.Image

import coinffeine.gui.application.main.MainView
import coinffeine.gui.application.operations.OperationsView
import coinffeine.gui.application.wallet.WalletView
import coinffeine.gui.application.{ApplicationProperties, ApplicationScene}
import coinffeine.gui.control.ConnectionStatusWidget
import coinffeine.gui.control.wallet.{BitcoinBalanceWidget, FiatBalanceWidget}
import coinffeine.gui.notification.NotificationManager
import coinffeine.gui.setup.SetupWizard
import coinffeine.model.bitcoin.{IntegrationTestNetworkComponent, KeyPair, Wallet}
import coinffeine.peer.api.impl.ProductionCoinffeineApp
import coinffeine.peer.config.user.LocalAppDataDir

object Main extends JFXApp
  with ProductionCoinffeineApp.Component with IntegrationTestNetworkComponent {

  if (mustRunWizard) {
    runSetupWizard()
  }

  val properties = new ApplicationProperties(app)
  val notificationManager = new NotificationManager(app)
  JFXApp.AUTO_SHOW = false

  val appStart = app.start(30.seconds)
  stage = new PrimaryStage {
    title = "Coinffeine"
    scene = new ApplicationScene(
      views = Seq(
        new MainView,
        new WalletView(app, properties.wallet),
        new OperationsView(app, properties)),
      toolbarWidgets = Seq(
        new BitcoinBalanceWidget(properties.wallet.balance),
        new FiatBalanceWidget(properties.fiatBalanceProperty)
      ),
      statusBarWidgets = Seq(
        new ConnectionStatusWidget(properties.connectionStatusProperty)
      )
    )
    icons.add(new Image(this.getClass.getResourceAsStream("/graphics/logo-128x128.png")))
  }
  stage.show()
  Await.result(appStart, Duration.Inf)

  override def stopApp(): Unit = {
    app.stopAndWait(30.seconds)
  }

  private def mustRunWizard: Boolean = configProvider.userConfig.isEmpty

  private def runSetupWizard(): Unit = {
    val keys = new KeyPair()
    val address = keys.toAddress(network)
    val setupConfig = new SetupWizard(address.toString).run()

    configProvider.saveUserSettings(
      configProvider.bitcoinSettings().copy(walletFile = createWallet(keys)))

    setupConfig.okPayWalletAccess.foreach { access =>
      val okPaySettings = configProvider.okPaySettings()
      configProvider.saveUserSettings(okPaySettings.copy(
        userAccount = Some(access.walletId),
        seedToken = Some(access.seedToken)
      ))
    }
  }

  private def createWallet(keys: KeyPair): File = {
    val wallet = new Wallet(network)
    wallet.importKey(keys)
    val walletFile = LocalAppDataDir.getFile("user.wallet", ensureCreated = false).toFile
    wallet.saveToFile(walletFile)
    walletFile
  }
}
