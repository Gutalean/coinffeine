package coinffeine.peer.payment.okpay

import java.net.URI
import scala.concurrent.duration._

import coinffeine.model.currency._
import coinffeine.model.payment.PaymentProcessor.{AccountId, AccountSecret}

case class OkPaySettings(
    userAccount: Option[AccountId],
    seedToken: Option[AccountSecret],
    verificationStatus: Option[VerificationStatus],
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

sealed trait VerificationStatus {
  def periodicLimits: FiatAmounts
}

object VerificationStatus {

  val values = Set(NotVerified, Verified)

  case object NotVerified extends VerificationStatus {
    override def periodicLimits = FiatAmounts.fromAmounts(300.EUR, 300.USD)
  }

  case object Verified extends VerificationStatus {
    override def periodicLimits = FiatAmounts.fromAmounts(100000.EUR, 100000.USD)
  }

  def parse(status: String): Option[VerificationStatus] = values.find(_.toString == status)
}
