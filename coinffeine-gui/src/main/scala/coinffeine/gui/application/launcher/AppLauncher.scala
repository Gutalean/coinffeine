package coinffeine.gui.application.launcher

import scalafx.Includes._
import scala.concurrent.Future
import scalafx.application.JFXApp.PrimaryStage
import scalafx.stage.Stage

import coinffeine.gui.application.main.CoinffeineMainStage
import coinffeine.gui.application.splash.SplashScreen
import coinffeine.gui.util.FxExecutor
import coinffeine.peer.api.impl.ProductionCoinffeineComponent

trait AppLauncher { this: ProductionCoinffeineComponent =>

  implicit private val executor = FxExecutor.asContext

  def launchApp(stage: PrimaryStage): Future[Stage] = {
    // FIXME: the laziness provided by the comprehension is needed since the cake does not properly
    //        manage the life cycle and race conditions arise
    SplashScreen.displayOn(stage)
    for {
      _ <- new ConfigureLogAction(configProvider).apply()
      _ <- new AcquirePidFileAction(configProvider.dataPath).apply()
      _ <- new RunWizardAction(configProvider, stage.scene.value.getWindow, network).apply()
      _ <- new AppStartAction(app).apply()
      _ <- new CheckForUpdatesAction().apply()
    } yield {
      stage.close()
      new CoinffeineMainStage(app, configProvider)
    }
  }
}
