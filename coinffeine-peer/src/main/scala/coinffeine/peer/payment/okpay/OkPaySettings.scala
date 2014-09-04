package coinffeine.peer.payment.okpay

import java.net.URI
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

import com.typesafe.config.Config

case class OkPaySettings(
  userAccount: String,
  seedToken: String,
  serverEndpoint: URI,
  pollingInterval: FiniteDuration
)

object OkPaySettings {

  def apply(config: Config): OkPaySettings = OkPaySettings(
    userAccount = config.getString("coinffeine.okpay.id"),
    seedToken = config.getString("coinffeine.okpay.token"),
    serverEndpoint = URI.create(config.getString("coinffeine.okpay.endpoint")),
    pollingInterval =
      config.getDuration("coinffeine.okpay.pollingInterval", TimeUnit.MILLISECONDS).millis
  )
}
