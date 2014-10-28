package coinffeine.model.network

import coinffeine.common.test.UnitTest

class PeerIdTest extends UnitTest {

  "A peer id" should "be converted to string" in {
    PeerId("123456789abcdef").toString shouldBe "peer:123456789abcdef"
  }

  it should "reject not hexadecimal values" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      PeerId("12 non hex id")
    }
  }

  it should "ignore casing" in {
    PeerId("12F") shouldBe PeerId("12f")
    PeerId("12F").hashCode() shouldBe PeerId("12f").hashCode()
  }

  it should "ignore leading zeroes" in {
    PeerId("012f") shouldBe PeerId("12f")
    PeerId("012f").hashCode() shouldBe PeerId("12f").hashCode()
  }

  it should "reject values longer than 40 hex digits (160 bits)" in {
    PeerId("f" * 40) should not be null
    an [IllegalArgumentException] shouldBe thrownBy {
      PeerId("f" * 41)
    }
  }

  it should "be generated randomly" in {
    PeerId.random() should not be PeerId.random()
  }
}
