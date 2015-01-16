package coinffeine.overlay

import coinffeine.common.test.UnitTest

class NetworkStatusTest extends UnitTest {
  "Network status" should "not have negative network sizes" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      OverlayNetwork.NetworkStatus(-1)
    }
  }
}
