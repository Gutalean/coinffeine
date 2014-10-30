package coinffeine.gui

import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scalafx.application.JFXApp

import org.controlsfx.dialog.{DialogStyle, Dialogs}
import coinffeine.gui.application.launcher.AppLauncher
import coinffeine.model.bitcoin.IntegrationTestNetworkComponent
import coinffeine.peer.api.impl.ProductionCoinffeineApp

object Main extends JFXApp
  with ProductionCoinffeineApp.Component with IntegrationTestNetworkComponent with AppLauncher {

  private val issueReportingResource = "https://github.com/coinffeine/coinffeine/issues"

  launchApp() match {
    case Success(s) => stage = s
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
