package coinffeine.gui

import scala.util.{Failure, Success}
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.control.Alert
import scalafx.scene.control.Alert.AlertType

import com.typesafe.scalalogging.LazyLogging

import coinffeine.gui.application.launcher.{AcquirePidFileAction, AppLauncher}
import coinffeine.gui.util.FxExecutor
import coinffeine.gui.wizard.Wizard
import coinffeine.peer.api.impl.ProductionCoinffeineComponent

object Main extends JFXApp with ProductionCoinffeineComponent with AppLauncher with LazyLogging {

  override def commandLineArgs = parameters.raw.toList

  private val issueReportingResource = "https://github.com/coinffeine/coinffeine/issues"

  stage = new PrimaryStage
  launchApp(stage).onComplete {
    case Success(s) =>
      s.show()
    case Failure(_: Wizard.CancelledByUser) =>
      logger.info("Exiting after wizard cancellation.")
      System.exit(0)
    case Failure(AcquirePidFileAction.AlreadyRunning(pid)) =>
      new Alert(AlertType.Information) {
        title = "Cannot start"
        headerText = "Coinffeine is already running"
        contentText = s"Process id: $pid"
      }.showAndWait()
      System.exit(-1)
    case Failure(exception) =>
      new Alert(AlertType.Error) {
        title = "Cannot start"
        headerText =
          "An unexpected error was thrown while starting Coinffeine app"
        contentText =
          s"""If the error persists, please report in:
            | $issueReportingResource
            |
            | $exception""".stripMargin
      }.showAndWait()
      System.exit(-1)
  }(FxExecutor.asContext)

  override def stopApp(): Unit = {
    app.stopAndWait()
  }
}
