package coinffeine.peer.api.impl

import scalaz.syntax.std.option._
import coinffeine.common.test.UnitTest

class SystemNameTest extends UnitTest {

  "The system name" should "be chosen using a random number when no hint is provided" in {
    SystemName.choose(hint = None) should fullyMatch regex """app-\d+"""
  }

  it should "be chosen using the hint when present" in {
    SystemName.choose(hint = "buyer".some) shouldBe "app-buyer"
    SystemName.choose(hint = "wallet-4".some) shouldBe "app-wallet-4"
  }

  it should "contain only valid characters" in {
    SystemName.choose(hint = "no spaces".some) shouldBe "app-no_spaces"
    SystemName.choose(hint = "%%hello%%".some) shouldBe "app-__hello__"
  }
}
