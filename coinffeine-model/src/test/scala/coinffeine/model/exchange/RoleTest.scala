package coinffeine.model.exchange

import scalaz.syntax.std.option._
import coinffeine.common.test.UnitTest

class RoleTest extends UnitTest {

  "A role" should "be parsed from string" in {
    Role.fromString("buyer") shouldBe BuyerRole.some
    Role.fromString("seller") shouldBe SellerRole.some
  }

  it should "not be parsed from an invalid string" in {
    Role.fromString("broker") shouldBe 'empty
  }
}
