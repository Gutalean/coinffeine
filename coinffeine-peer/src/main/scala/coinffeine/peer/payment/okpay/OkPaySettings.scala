package coinffeine.peer.payment.okpay

import java.net.URI
import scala.concurrent.duration._

import coinffeine.model.payment.PaymentProcessor.{AccountId, AccountSecret}

case class OkPaySettings(
    userAccount: Option[AccountId],
    seedToken: Option[AccountSecret],
    serverEndpointOverride: Option[URI],
    pollingInterval: FiniteDuration) {

  def apiCredentials: Option[OkPayApiCredentials] = for {
    walletId <- userAccount
    token <- seedToken
  } yield OkPayApiCredentials(walletId, token)

  def withApiCredentials(apiCredentials: OkPayApiCredentials) = copy(
    userAccount = Some(apiCredentials.walletId),
    seedToken = Some(apiCredentials.seedToken)
  )

  require(
    userAccount.forall(_.matches(OkPaySettings.AccountIdPattern)),
    s"Invalid OKPay account ID $userAccount")
}

object OkPaySettings {
  val AccountIdPattern = "[a-zA-Z0-9-_]+"
}
