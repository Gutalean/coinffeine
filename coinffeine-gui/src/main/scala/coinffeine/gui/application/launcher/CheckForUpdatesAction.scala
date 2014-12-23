package coinffeine.gui.application.launcher

import java.net.URI
import scala.util.{Failure, Success, Try}

import com.typesafe.scalalogging.LazyLogging
import org.controlsfx.dialog.{Dialog, DialogStyle, Dialogs}

import coinffeine.gui.application.updates.HttpConfigVersionChecker
import coinffeine.gui.util.{Browser, FxExecutor}

class CheckForUpdatesAction extends LazyLogging {

  private implicit val executor = FxExecutor.asContext

  def apply() = Try {
    val checker = new HttpConfigVersionChecker()
    logger.info("Checking for new versions of Coinffeine app... ")
    val checking = checker.canUpdateTo()
    checking.onComplete {
      case Success(Some(version)) =>
        val answer = Dialogs.create()
          .title("New version available")
          .message(s"There is a new version available ($version). Do you want to download it?")
          .style(DialogStyle.NATIVE)
          .actions(Dialog.Actions.NO, Dialog.Actions.YES)
          .showConfirm()
        if (answer == Dialog.Actions.YES) {
          Browser.default.browse(CheckForUpdatesAction.DownloadsUrl)
        }
      case Success(None) =>
        logger.info("Coinffeine app is up to date")
      case Failure(cause) =>
        logger.error("cannot retrieve information of newest versions", cause)
    }
    checking.onComplete(_ => checker.shutdown())
  }
}

object CheckForUpdatesAction {

  val DownloadsUrl = new URI("http://github.com/coinffeine/coinffeine")
}
