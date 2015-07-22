package coinffeine.peer.payment.okpay.ws

import coinffeine.common.test.UnitTest

class SSomeTest extends UnitTest {

  "Nested option values" should "be generated" in {
    SSome("hello") shouldBe Some(Some("hello"))
  }

  it should "be matched" in {
    SSome.unapply(None) shouldBe 'empty
    SSome.unapply(Some(None)) shouldBe 'empty
    SSome.unapply(Some(Some(13))) shouldBe Some(13)
  }
}
