package coinffeine.gui.application.updates

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import com.typesafe.config.{ConfigFactory, Config}
import dispatch.{Http, as, url}

class HttpConfigFactory extends ConfigVersionChecker.ConfigFactory {

  override def apply(): Future[Config] =
    Http(HttpConfigFactory.FileLocation OK as.String).map(ConfigFactory.parseString)
}

object HttpConfigFactory {
  val FileLocation = url(
    "https://raw.githubusercontent.com/Coinffeine/coinffeine/master/VERSION")
}
