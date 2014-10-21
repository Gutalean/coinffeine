package coinffeine.gui.application.updates

import scala.concurrent.{Future, ExecutionContext}
import scala.util.Try
import scala.util.control.NonFatal

import com.typesafe.config.Config

class ConfigVersionChecker(
    configFactory: ConfigVersionChecker.ConfigFactory) extends VersionChecker {

  override def latestStableVersion()(implicit executor: ExecutionContext) = {
    configFactory().map { config =>
      extractCurrent(config)
    }.recoverWith {
      case NonFatal(e) => Future.failed(new VersionChecker.VersionFetchingException(
        "something went wrong while obtaining latest estable version from config", e))
    }
  }

  private def extractCurrent(config: Config): CoinffeineVersion = CoinffeineVersion(
    major = config.getInt("latest-stable.major"),
    minor = config.getInt("latest-stable.minor"),
    revision = config.getInt("latest-stable.revision"),
    build = Try(config.getString("latest-stable.build")).getOrElse("")
  )
}

object ConfigVersionChecker {

  type ConfigFactory = () => Future[Config]
}
