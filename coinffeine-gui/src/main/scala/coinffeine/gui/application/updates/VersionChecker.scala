package coinffeine.gui.application.updates

import scala.concurrent.{ExecutionContext, Future}

trait VersionChecker {

  def latestStableVersion()(implicit executor: ExecutionContext): Future[CoinffeineVersion]

  def canUpdateTo()(implicit executor: ExecutionContext): Future[Option[CoinffeineVersion]] =
    latestStableVersion().map { v =>
      if (v.isNewerThan(CoinffeineVersion.Current)) Some(v)
      else None
    }
}

object VersionChecker {

  case class VersionFetchingException(msg: String, cause: Throwable = null)
    extends Exception(msg, cause)
}
