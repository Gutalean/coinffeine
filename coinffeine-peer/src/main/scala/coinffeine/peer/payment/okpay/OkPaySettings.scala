package coinffeine.peer.payment.okpay

import java.net.URI
import scala.concurrent.duration._

case class OkPaySettings(
  userAccount: String,
  seedToken: String,
  serverEndpoint: URI,
  pollingInterval: FiniteDuration
)
