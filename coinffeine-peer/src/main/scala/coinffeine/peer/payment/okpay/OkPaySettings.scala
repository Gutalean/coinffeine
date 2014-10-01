package coinffeine.peer.payment.okpay

import java.net.URI
import scala.concurrent.duration._

case class OkPaySettings(
  userAccount: Option[String],
  seedToken: Option[String],
  serverEndpoint: URI,
  pollingInterval: FiniteDuration
)
