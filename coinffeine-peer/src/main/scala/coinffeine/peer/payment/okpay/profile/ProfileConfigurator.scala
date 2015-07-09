package coinffeine.peer.payment.okpay.profile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging

import coinffeine.model.payment.okpay.VerificationStatus
import coinffeine.peer.payment.okpay.OkPayApiCredentials

class ProfileConfigurator(profile: Profile) extends LazyLogging {

  import ProfileConfigurator._

  def configure(): Future[Result] = Future {
    ensureBusinessMode()
    val walletId = profile.walletId()
    profile.enableAPI(walletId)

    Result(
      credentials = OkPayApiCredentials(walletId, profile.configureSeedToken(walletId)),
      verificationStatus = profile.verificationStatus
    )
  }

  private def ensureBusinessMode(): Unit = {
    if (profile.accountMode != AccountMode.Business) switchToBusinessMode()
    else logger.info("Profile already in business mode")
  }

  private def switchToBusinessMode(): Unit = {
    profile.accountMode = AccountMode.Business
  }
}

object ProfileConfigurator {
  case class Result(credentials: OkPayApiCredentials, verificationStatus: VerificationStatus)
}
