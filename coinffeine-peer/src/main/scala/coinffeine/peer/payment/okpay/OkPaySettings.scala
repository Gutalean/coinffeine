package coinffeine.peer.payment.okpay

import java.net.URI
import scala.concurrent.duration._

case class OkPaySettings(
    userAccount: Option[String],
    seedToken: Option[String],
    serverEndpointOverride: Option[URI],
    pollingInterval: FiniteDuration) {

  require(
    userAccount.forall(_.matches(OkPaySettings.AccountIdPattern)),
    s"Invalid OKPay account ID $userAccount")
}

object OkPaySettings {
  val AccountIdPattern = "[a-zA-Z0-9-_]+"
}
