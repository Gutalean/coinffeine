package coinffeine.gui

import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scalafx.application.JFXApp

import com.typesafe.scalalogging.LazyLogging
import org.controlsfx.dialog.{DialogStyle, Dialogs}

import coinffeine.gui.application.launcher.{AcquirePidFileAction, AppLauncher}
import coinffeine.gui.wizard.Wizard
import coinffeine.peer.api.impl.ProductionCoinffeineComponent

object Main extends JFXApp with ProductionCoinffeineComponent with AppLauncher with LazyLogging {

  override def commandLineArgs = parameters.raw.toList

  private val issueReportingResource = "https://github.com/coinffeine/coinffeine/issues"

  LogConfigurator.configure(configProvider)

  launchApp() match {
    case Success(s) => stage = s
    case Failure(_: Wizard.CancelledByUser) =>
      logger.info("Exiting after wizard cancellation.")
      System.exit(0)
    case Failure(AcquirePidFileAction.AlreadyRunning(pid)) =>
      Dialogs.create()
        .message(s"Coinffeine is already running (pid $pid)")
        .style(DialogStyle.NATIVE)
        .showInformation()
      System.exit(-1)
    case Failure(e) =>
      Dialogs.create()
        .message("An unexpected error was thrown while starting Coinffeine app. " +
          "If the error persists, please report in:\n\n" + issueReportingResource)
        .style(DialogStyle.NATIVE)
        .showException(e)
      System.exit(-1)
  }

  override def stopApp(): Unit = {
    app.stopAndWait(30.seconds)
  }
}
