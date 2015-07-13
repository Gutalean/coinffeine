package coinffeine.gui.application.updates

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

import com.typesafe.config.Config

import coinffeine.peer.AppVersion

class ConfigVersionChecker(configProvider: ConfigVersionChecker.ConfigProvider)
  extends VersionChecker {

  override def latestStableVersion()(implicit executor: ExecutionContext) = {
    configProvider().map { config =>
      extractCurrent(config)
    }.recoverWith {
      case NonFatal(e) => Future.failed(new VersionChecker.VersionFetchingException(
        "something went wrong while obtaining latest stable version from config", e))
    }
  }

  override def shutdown() = configProvider.shutdown()

  private def extractCurrent(config: Config) = AppVersion(
    major = config.getInt("latest-stable.major"),
    minor = config.getInt("latest-stable.minor"),
    revision = config.getInt("latest-stable.revision"),
    tag = Try(config.getString("latest-stable.build")).toOption
  )
}

object ConfigVersionChecker {
  trait ConfigProvider {
    def apply(): Future[Config]
    def shutdown(): Unit
  }
}
