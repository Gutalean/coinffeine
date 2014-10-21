package coinffeine.gui.application.launcher

import scala.util.Try
import scalafx.application.JFXApp.PrimaryStage

import coinffeine.model.bitcoin.IntegrationTestNetworkComponent
import coinffeine.peer.api.impl.ProductionCoinffeineApp

trait AppLauncher { this: ProductionCoinffeineApp.Component with IntegrationTestNetworkComponent =>

  private val runWizardAction = new RunWizardAction(configProvider, network)
  private val notificationManagerRunAction = new NotificationManagerRunAction(app)
  private val appStartAction = new AppStartAction(app)
  private val checkForUpdatesAction = new CheckForUpdatesAction()
  private val displayMainWindowAction = new DisplayMainWindowAction(app, configProvider)

  def launchApp(): Try[PrimaryStage] = {
    for {
      _ <- runWizardAction()
      _ <- notificationManagerRunAction()
      _ <- appStartAction()
      _ <- checkForUpdatesAction()
      stage <- displayMainWindowAction()
    } yield stage
  }
}
