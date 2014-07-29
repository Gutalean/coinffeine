package coinffeine.protocol.messages.brokerage

import coinffeine.common.test.UnitTest
import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.Implicits._
import coinffeine.model.market.{Bid, OrderBookEntry, OrderId}

class PeerPositionsTest extends UnitTest {

  "Peer orders" should "have the same currency" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      PeerPositions(Market(Euro), Seq(OrderBookEntry(OrderId.random(), Bid, 1.BTC, 400.USD)))
    }
  }
}
