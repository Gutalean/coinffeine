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

  it should "reject values with uppercase letters" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      PeerId("12F")
    }
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
