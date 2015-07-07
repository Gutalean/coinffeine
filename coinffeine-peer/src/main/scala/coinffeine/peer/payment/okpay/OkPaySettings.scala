package coinffeine.peer.payment.okpay

import java.net.URI
import scala.concurrent.duration._

import coinffeine.model.currency.FiatAmounts
import coinffeine.model.payment.PaymentProcessor.{AccountId, AccountSecret}
import coinffeine.model.payment.okpay.VerificationStatus

case class OkPaySettings(
    userAccount: Option[AccountId],
    seedToken: Option[AccountSecret],
    verificationStatus: Option[VerificationStatus],
    customPeriodicLimits: Option[FiatAmounts],
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

