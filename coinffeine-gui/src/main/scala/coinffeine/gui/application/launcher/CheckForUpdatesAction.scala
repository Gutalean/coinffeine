package coinffeine.gui.application.launcher

import java.awt.Desktop
import java.net.URI
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import org.controlsfx.dialog.{Dialog, DialogStyle, Dialogs}
import org.slf4j.LoggerFactory

import coinffeine.gui.application.updates.{CoinffeineVersion, HttpConfigVersionChecker}
import coinffeine.gui.util.FxExecutor

class CheckForUpdatesAction {

  private implicit val executor = FxExecutor.asContext

  private val log = LoggerFactory.getLogger(this.getClass)

  def apply() = Try {
    val checker = new HttpConfigVersionChecker()
    log.info("Checking for new versions of Coinffeine app... ")
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
          Desktop.getDesktop.browse(CheckForUpdatesAction.DownloadsUrl)
        }
      case Success(None) =>
        log.info("Coinffeine app is up to date")
      case Failure(e) =>
        log.error("cannot retrieve information of newest versions", e)
    }
    checking.onComplete(_ => checker.shutdown())
  }
}

object CheckForUpdatesAction {

  val DownloadsUrl = new URI("http://github.com/coinffeine/coinffeine")
}
