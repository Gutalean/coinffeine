package coinffeine.gui.application.updates

import scala.concurrent.{ExecutionContext, Future}

import coinffeine.peer.AppVersion

trait VersionChecker {

  def latestStableVersion()(implicit executor: ExecutionContext): Future[AppVersion]

  def canUpdateTo()(implicit executor: ExecutionContext): Future[Option[AppVersion]] =
    latestStableVersion().map { v =>
      if (v.isNewerThan(AppVersion.Current)) Some(v)
      else None
    }

  def shutdown(): Unit
}

object VersionChecker {

  case class VersionFetchingException(msg: String, cause: Throwable = null)
    extends Exception(msg, cause)
}
