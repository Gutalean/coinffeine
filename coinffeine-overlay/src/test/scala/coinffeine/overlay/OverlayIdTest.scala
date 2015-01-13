package coinffeine.overlay

import coinffeine.common.test.UnitTest

class OverlayIdTest extends UnitTest {

  "An overlay id" should "be positive" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      OverlayId(-1)
    }
  }

  it should "fill up to 160 bits" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      OverlayId(BigInt(0).setBit(161))
    }
  }

  it should "have a compact hexadecimal representation" in {
    OverlayId(0).toString shouldBe "OverlayId(0)"
    OverlayId(OverlayId.MaxValue).toString shouldBe
      "OverlayId(ffffffffffffffffffffffffffffffffffffffff)"
  }
}
