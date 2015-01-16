package coinffeine.overlay.relay.settings

import scala.concurrent.duration._

import coinffeine.common.test.UnitTest

class RelaySettingsTest extends UnitTest {

  val settings = RelaySettings(serverAddress = "host", serverPort = 1234)

  "Relay settings" should "be converted to server relay settings" in {
    settings.serverSettings.bindAddress shouldBe settings.serverAddress
    settings.serverSettings.bindPort shouldBe settings.serverPort
  }

  it should "be converted to client relay settings" in {
    settings.clientSettings.host shouldBe settings.serverAddress
    settings.clientSettings.port shouldBe settings.serverPort
  }

  it should "reject negative frame sizes" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      settings.copy(maxFrameBytes = -1)
    }
  }

  it should "reject non-positive timeouts" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      settings.copy(connectionTimeout = 0.seconds)
    }
    an [IllegalArgumentException] shouldBe thrownBy {
      settings.copy(identificationTimeout = 0.seconds)
    }
  }
}
