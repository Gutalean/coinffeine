package coinffeine.gui.application.launcher

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.ButtonType

import com.typesafe.scalalogging.LazyLogging

import coinffeine.gui.application.updates.HttpConfigVersionChecker
import coinffeine.gui.scene.CoinffeineAlert
import coinffeine.gui.util.{Browser, FxExecutor}
import coinffeine.peer.AppVersion

class CheckForUpdatesAction extends LazyLogging {

  def apply(): Future[Unit] = {
    val checker = new HttpConfigVersionChecker()
    logger.info("Checking for new versions of Coinffeine app... ")

    val result = checker.canUpdateTo()(ExecutionContext.global)
      .map { newerVersion =>
        newerVersion.fold(upToDate())(suggestDownloading)
      }(FxExecutor.asContext)
      .recover {
        case NonFatal(cause) => logger.error("cannot retrieve information of newest versions", cause)
      }(ExecutionContext.global)
    result.onComplete(_ => checker.shutdown())(ExecutionContext.global)
    result
  }

  private def upToDate(): Unit = {
    logger.info("Coinffeine app is up to date")
  }

  private def suggestDownloading(version: AppVersion): Unit = {
    logger.info("Coinffeine app is outdated: {} is available", version)
    val dialog = new CoinffeineAlert(AlertType.Confirmation, prefSize = Some(450, 300)) {
      title = "New version available"
      headerText = "New version available"
      contentText = s"There is a new version available ($version). Do you want to download it?"
      buttonTypes = Seq(ButtonType.Yes, ButtonType.No)
    }
    if (dialog.showAndWait().contains(ButtonType.Yes)) {
      Browser.default.browse(CheckForUpdatesAction.DownloadsUrl)
    }
  }
}

object CheckForUpdatesAction {
  val DownloadsUrl = new URI("http://www.coinffeine.com/download.html")
}
