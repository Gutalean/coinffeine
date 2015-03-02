package coinffeine.gui.application.launcher

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import com.typesafe.scalalogging.LazyLogging
import org.controlsfx.dialog.{Dialog, DialogStyle, Dialogs}

import coinffeine.gui.application.updates.HttpConfigVersionChecker
import coinffeine.gui.util.{Browser, FxExecutor}

class CheckForUpdatesAction extends LazyLogging {

  def apply(): Future[Unit] = {
    val checker = new HttpConfigVersionChecker()
    logger.info("Checking for new versions of Coinffeine app... ")

    val result = checker.canUpdateTo()(ExecutionContext.global)
      .map {
        case Some(version) =>
          val answer = Dialogs.create()
            .title("New version available")
            .message(s"There is a new version available ($version). Do you want to download it?")
            .style(DialogStyle.NATIVE)
            .actions(Dialog.Actions.NO, Dialog.Actions.YES)
            .showConfirm()
          if (answer == Dialog.Actions.YES) {
            Browser.default.browse(CheckForUpdatesAction.DownloadsUrl)
          }
        case None =>
          logger.info("Coinffeine app is up to date")
      }(FxExecutor.asContext)
      .recover {
        case NonFatal(cause) =>
          logger.error("cannot retrieve information of newest versions", cause)
      }(ExecutionContext.global)
    result.onComplete(_ => checker.shutdown())(ExecutionContext.global)
    result
  }
}

object CheckForUpdatesAction {

  val DownloadsUrl = new URI("http://www.coinffeine.com/download.html")
}
