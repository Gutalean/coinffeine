package coinffeine.overlay.relay.messages

import coinffeine.common.test.UnitTest

class StatusMessageTest extends UnitTest {

  "A status message" should "report non-negative network sizes" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      StatusMessage(-1)
    }
    StatusMessage(0) should not be null
  }
}
