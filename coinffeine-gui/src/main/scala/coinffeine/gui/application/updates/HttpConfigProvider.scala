package coinffeine.gui.application.updates

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.typesafe.config.{Config, ConfigFactory}
import dispatch.{Http, as, url}

import coinffeine.peer.net.DaemonHttpClient

class HttpConfigProvider extends ConfigVersionChecker.ConfigProvider {

  private val daemonHttp = new DaemonHttpClient
  private val http = new Http(daemonHttp.client)

  override def apply(): Future[Config] = Future {
    // `http.apply()` returns a future, but it's execution takes a long time (~1 second),
    // perhaps due to backend (Netty) machinery initialization. So we execute everything in
    // another future to ensure the function evaluates immediately.
    http(url(HttpConfigProvider.FileLocation) OK as.String).map(ConfigFactory.parseString)
  }.flatMap(identity)

  override def shutdown(): Unit = {
    http.shutdown()
    daemonHttp.shutdown()
  }
}

object HttpConfigProvider {
  val FileLocation = "http://www.coinffeine.com/version/current"
}
