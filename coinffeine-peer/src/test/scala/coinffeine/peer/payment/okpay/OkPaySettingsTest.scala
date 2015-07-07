package coinffeine.peer.payment.okpay

import scala.concurrent.duration._
import scalaz.syntax.std.option._

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.payment.okpay.VerificationStatus

class OkPaySettingsTest extends UnitTest {

  val settingsWithNoCredentials = OkPaySettings(
    userAccount = None,
    seedToken = None,
    verificationStatus = None,
    customPeriodicLimits = None,
    serverEndpointOverride = None,
    pollingInterval = 10.seconds
  )

  "OKPay settings" should "contain valid account ids when present" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      settingsWithNoCredentials.copy(userAccount = "%&&   invalid  :?¿".some)
    }
  }

  it should "update and provide API credentials" in {
    val credentials = OkPayApiCredentials("ID1234", "Token5678")
    val completeSettings = settingsWithNoCredentials.withApiCredentials(credentials)
    completeSettings.apiCredentials shouldBe Some(credentials)
  }

  it should "take the unverified periodic limits by default" in {
    settingsWithNoCredentials.periodicLimits shouldBe
        VerificationStatus.NotVerified.periodicLimits
  }

  it should "take the periodic limits of the configured verification status" in {
    for (status <- VerificationStatus.values) {
      val settings = settingsWithNoCredentials.copy(verificationStatus = Some(status))
      settings.periodicLimits shouldBe status.periodicLimits
    }
  }

  it should "take custom periodic limits over default limits" in {
    val customPeriodicLimits = FiatAmounts.fromAmounts(1000.EUR, 300.USD)
    val settings = settingsWithNoCredentials.copy(
      customPeriodicLimits = Some(customPeriodicLimits))
    settings.periodicLimits shouldBe customPeriodicLimits
  }
}
