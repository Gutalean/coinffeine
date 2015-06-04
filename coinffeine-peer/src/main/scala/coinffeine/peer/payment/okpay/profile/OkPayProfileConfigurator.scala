package coinffeine.peer.payment.okpay.profile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging

import coinffeine.peer.payment.okpay.OkPayApiCredentials

class OkPayProfileConfigurator(profile: ScrappingProfile) extends LazyLogging {

  def configure(): Future[OkPayApiCredentials] = Future {
    ensureBusinessMode()
    val walletId = profile.walletId()
    profile.enableAPI(walletId)
    OkPayApiCredentials(walletId, profile.configureSeedToken(walletId))
  }

  private def ensureBusinessMode(): Unit = {
    if (profile.accountMode != AccountMode.Business) switchToBusinessMode()
    else logger.info("Profile already in business mode")
  }

  private def switchToBusinessMode(): Unit = {
    profile.accountMode = AccountMode.Business
  }
}
