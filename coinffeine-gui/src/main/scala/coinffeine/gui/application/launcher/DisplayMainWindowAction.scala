package coinffeine.gui.application.launcher

import scala.util.Try
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.image.Image

import coinffeine.gui.application.main.MainView
import coinffeine.gui.application.operations.OperationsView
import coinffeine.gui.application.wallet.WalletView
import coinffeine.gui.application.{ApplicationProperties, ApplicationScene}
import coinffeine.gui.control.ConnectionStatusWidget
import coinffeine.gui.control.wallet.{BitcoinBalanceWidget, FiatBalanceWidget}
import coinffeine.peer.api.CoinffeineApp
import coinffeine.peer.config.ConfigProvider

class DisplayMainWindowAction(app: CoinffeineApp,
                              configProvider: ConfigProvider) extends LaunchAction[PrimaryStage] {
  override def apply() = Try {
    val properties = new ApplicationProperties(app)
    val stage = new PrimaryStage {
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
        ),
        settingsProvider = configProvider
      )
      icons.add(new Image(this.getClass.getResourceAsStream("/graphics/logo-128x128.png")))
    }
    stage.show()
    stage
  }
}
