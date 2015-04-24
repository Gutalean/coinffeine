package coinffeine.gui.application.main

import scalafx.scene.image.Image
import scalafx.stage.{Stage, StageStyle}

import coinffeine.gui.application.operations.OperationsView
import coinffeine.gui.application.operations.validation.DefaultOrderValidation
import coinffeine.gui.application.stats.StatsView
import coinffeine.gui.application.wallet.WalletView
import coinffeine.gui.application.{ApplicationProperties, ApplicationScene}
import coinffeine.gui.control.ConnectionStatusWidget
import coinffeine.gui.util.FxExecutor
import coinffeine.peer.api.CoinffeineApp
import coinffeine.peer.config.ConfigProvider

class CoinffeineMainStage(app: CoinffeineApp,
                          configProvider: ConfigProvider) extends Stage(StageStyle.DECORATED) {

  private val properties = new ApplicationProperties(app, FxExecutor.asContext)
  private val orderValidator = new DefaultOrderValidation(app)

  title = "Coinffeine"
  scene = new ApplicationScene(
    balances = ApplicationScene.Balances(properties.wallet.balance, properties.fiatBalanceProperty),
    views = Seq(
      new OperationsView(app, properties, orderValidator),
      new StatsView(app),
      new WalletView(app.wallet, properties.wallet)
    ),
    statusBarWidgets = Seq(
      new ConnectionStatusWidget(properties.connectionStatusProperty)
    ),
    settingsProvider = configProvider
  )
  icons.add(new Image(this.getClass.getResourceAsStream("/graphics/logo-256x256.png")))
}
