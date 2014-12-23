package coinffeine.gui.application.launcher

import scala.util.Try
import scalafx.application.JFXApp.PrimaryStage

import coinffeine.gui.application.main.CoinffeinePrimaryStage
import coinffeine.peer.api.impl.ProductionCoinffeineComponent

trait AppLauncher { this: ProductionCoinffeineComponent =>

  def launchApp(): Try[PrimaryStage] = {
    // FIXME: the laziness provided by the comprehension is needed since the cake does not properly
    //        manage the life cycle and race conditions arise
    for {
      _ <- new AcquirePidFileAction().apply()
      _ <- new RunWizardAction(configProvider, network).apply()
      _ <- new AppStartAction(app).apply()
      _ <- new CheckForUpdatesAction().apply()
    } yield new CoinffeinePrimaryStage(app, configProvider)
  }
}
