package coinffeine.overlay.relay.settings

import scala.concurrent.duration._

import coinffeine.common.test.UnitTest

class RelayClientSettingsTest extends UnitTest {
  "Client config" should "require a positive connection timeout" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      RelayClientSettings("foo", 123, connectionTimeout = 0.seconds)
    }
  }

  it should "require a positive identification timeout" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      RelayClientSettings("foo", 123, identificationTimeout = 0.seconds)
    }
  }

  it should "require a positive max frame size" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      RelayClientSettings("foo", 123, maxFrameBytes = -3)
    }
  }
}
