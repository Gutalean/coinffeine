package coinffeine.model.order

import coinffeine.common.test.UnitTest
import coinffeine.model.ActivityLog
import coinffeine.model.currency._

class ArchivedOrderTest extends UnitTest {
  "An archived order" should "require a non-empty log of activities" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      ArchivedOrder(OrderId.random(), Bid, 10.BTC, MarketPrice(Euro), Map.empty, ActivityLog.empty)
    }
  }
}
