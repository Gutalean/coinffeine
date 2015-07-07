package coinffeine.peer.payment.okpay

import scala.concurrent.duration._
import scalaz.syntax.std.option._

import coinffeine.common.test.UnitTest

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
}
