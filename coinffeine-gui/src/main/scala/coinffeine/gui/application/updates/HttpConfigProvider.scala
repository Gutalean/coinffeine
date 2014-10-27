package coinffeine.gui.application.updates

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import com.typesafe.config.{ConfigFactory, Config}
import dispatch.{Http, as, url}

import coinffeine.peer.net.DaemonHttpClient

class HttpConfigProvider extends ConfigVersionChecker.ConfigProvider {

  private val daemonHttp = new DaemonHttpClient
  private val http = new Http(daemonHttp.client)

  override def apply(): Future[Config] =
    http(HttpConfigProvider.FileLocation OK as.String).map(ConfigFactory.parseString)

  override def shutdown(): Unit = {
    http.shutdown()
    daemonHttp.shutdown()
  }
}

object HttpConfigProvider {
  val FileLocation = url(
    "https://raw.githubusercontent.com/Coinffeine/coinffeine/master/VERSION")
}
