package coinffeine.overlay.relay

import scala.concurrent.duration._

import coinffeine.common.test.UnitTest

class ClientConfigTest extends UnitTest {
  "Client config" should "require a positive connection timeout" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      ClientConfig("foo", 123, connectionTimeout = 0.seconds)
    }
  }

  it should "require a positive max frame size" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      ClientConfig("foo", 123, maxFrameBytes = -3)
    }
  }
}
