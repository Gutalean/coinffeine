package coinffeine.peer.market.orders

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.market.OrderBookEntry
import coinffeine.model.order.{Bid, MarketPrice}

class SubmissionPolicyTest extends UnitTest {

  private val marketPriceBid = OrderBookEntry.random(Bid, 1.BTC, MarketPrice(Euro))

  "The submission policy" should "submit nothing if no entry is present" in new Fixture {
    policy.unsetEntry()
    policy.entryToSubmit shouldBe 'empty
  }

  it should "submit any available entry" in new Fixture {
    policy.setEntry(marketPriceBid)
    policy.entryToSubmit shouldBe Some(marketPriceBid)
  }

  trait Fixture {
    protected val policy = new SubmissionPolicyImpl
  }
}
