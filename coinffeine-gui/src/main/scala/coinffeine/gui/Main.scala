package coinffeine.gui

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Random
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.image.Image

import coinffeine.gui.application.main.MainView
import coinffeine.gui.application.operations.OperationsView
import coinffeine.gui.application.{ApplicationProperties, ApplicationScene, NotificationManager}
import coinffeine.gui.control.{ConnectionStatusWidget, WalletBalanceWidget}
import coinffeine.gui.setup.CredentialsValidator.Result
import coinffeine.gui.setup.{CredentialsValidator, SetupWizard}
import coinffeine.model.bitcoin.{KeyPair, IntegrationTestNetworkComponent}
import coinffeine.model.currency.Currency.{Bitcoin, Euro}
import coinffeine.peer.api.impl.ProductionCoinffeineApp
import coinffeine.peer.payment.okpay.OkPayCredentials

object Main extends JFXApp
  with ProductionCoinffeineApp.Component with IntegrationTestNetworkComponent {

  val properties = new ApplicationProperties(app)
  val notificationManager = new NotificationManager(app)
  JFXApp.AUTO_SHOW = false

  val validator = new CredentialsValidator {
    override def apply(credentials: OkPayCredentials): Future[Result] = Future {
      Thread.sleep(2000)
      if (Random.nextBoolean()) CredentialsValidator.ValidCredentials
      else CredentialsValidator.InvalidCredentials("Random failure")
    }
  }

  if (mustRunWizard) {
    runSetupWizard()
  }

  val appStart = app.start(30.seconds)
  stage = new PrimaryStage {
    title = "Coinffeine"
    scene = new ApplicationScene(
      views = Seq(new MainView, new OperationsView(app, properties)),
      toolbarWidgets = Seq(
        new WalletBalanceWidget(Bitcoin, properties.walletBalanceProperty),
        new WalletBalanceWidget(Euro, properties.fiatBalanceProperty)
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
    val setupConfig = new SetupWizard(address.toString, validator).run()

    val bitcoinSettings = configProvider.bitcoinSettings
    configProvider.saveUserSettings(
      bitcoinSettings.copy(walletPrivateKey = keys.getPrivateKeyEncoded(network).toString))

    setupConfig.okPayWalletAccess.foreach { access =>
      val okPaySettings = configProvider.okPaySettings
      configProvider.saveUserSettings(
        okPaySettings.copy(userAccount = access.walletId, seedToken = access.seedToken))
    }
  }
}
