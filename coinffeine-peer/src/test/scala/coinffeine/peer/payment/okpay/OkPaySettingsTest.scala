package coinffeine.peer.payment.okpay

import scala.concurrent.duration._
import scalaz.syntax.std.option._

import coinffeine.common.test.UnitTest

class OkPaySettingsTest extends UnitTest {

  "OKPay settings" should "contain valid account ids when present" in {
    val settingsWithNoAccount = OkPaySettings(
      userAccount = None,
      seedToken = None,
      serverEndpointOverride = None,
      pollingInterval = 10.seconds
    )
    an [IllegalArgumentException] shouldBe thrownBy {
      settingsWithNoAccount.copy(userAccount = "%&&   invalid  :?¿".some)
    }
  }
}
