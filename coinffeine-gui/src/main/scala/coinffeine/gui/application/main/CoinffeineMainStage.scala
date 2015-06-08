package coinffeine.gui.application.main

import scalafx.Includes._
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.ButtonType
import scalafx.scene.image.Image
import scalafx.stage.{Stage, StageStyle, WindowEvent}

import coinffeine.gui.application.operations.OperationsView
import coinffeine.gui.application.operations.validation.DefaultOrderValidation
import coinffeine.gui.application.stats.StatsView
import coinffeine.gui.application.wallet.WalletView
import coinffeine.gui.application.{ApplicationProperties, ApplicationScene}
import coinffeine.gui.control.ConnectionStatusWidget
import coinffeine.gui.scene.CoinffeineAlert
import coinffeine.gui.util.FxExecutor
import coinffeine.peer.api.CoinffeineApp
import coinffeine.peer.config.ConfigProvider

class CoinffeineMainStage(app: CoinffeineApp,
                          configProvider: ConfigProvider) extends Stage(StageStyle.DECORATED) {

  private val properties = new ApplicationProperties(app, FxExecutor.asContext)
  private val orderValidator = new DefaultOrderValidation(app)

  title = "Coinffeine"
  minWidth = 1024
  minHeight = 600
  scene = new ApplicationScene(
    balances = ApplicationScene.Balances(properties.wallet.balance, properties.fiatBalanceProperty),
    views = Seq(
      new OperationsView(app, properties, orderValidator),
      new StatsView(app),
      new WalletView(
        configProvider.bitcoinSettings().network,
        app.wallet,
        app.utils.bitcoinFeeCalculator,
        properties.wallet)
    ),
    statusBarWidgets = Seq(
      new ConnectionStatusWidget(properties.connectionStatusProperty)
    ),
    settingsProvider = configProvider
  )
  icons.add(new Image(this.getClass.getResourceAsStream("/graphics/logo-256x256.png")))

  onCloseRequest = (event: WindowEvent) => {
    if (exchangesRunning && !confirmQuittingWithRunningExchanges()) {
      event.consume()
    }
  }

  private def exchangesRunning: Boolean = app.network.exchanges.exists(!_.isCompleted)

  private def confirmQuittingWithRunningExchanges(): Boolean = {
    new CoinffeineAlert(AlertType.Confirmation) {
      title = "Close Coinffeine"
      headerText = "Some exchanges are still running"
      contentText =
        "There are some orders that have running exchanges. If you quit the application " +
          "the exchanges will not continue until the application is launched again. If it is " +
          "not launched again after several hours, the exchange will be timed out and you " +
          "will loose the deposits.\n\n" +
          "It is highly recommended to wait for the exchanges to complete before closing " +
          "the application.\n\n" +
          "Do you really want to close Coinffeine?"
    }.showAndWait()
      .contains(ButtonType.OK)
  }
}
