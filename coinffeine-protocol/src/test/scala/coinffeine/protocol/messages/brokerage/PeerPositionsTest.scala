package coinffeine.protocol.messages.brokerage

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.model.order.{OrderId, Bid, Price}

class PeerPositionsTest extends UnitTest {

  private val market = Market(Euro)

  "Peer positions" should "create a different nonce for each positions object" in {
    val pos1 = PeerPositions(market, Seq(randomBid()))
    val pos2 = PeerPositions(market, Seq(randomBid()))
    pos1.nonce should not equal pos2.nonce
  }

  it should "require no repeated order id" in {
    val positions = PeerPositions.empty(market).addEntry(randomBid())
    an [IllegalArgumentException] shouldBe thrownBy {
      positions.addEntry(positions.entries.head)
    }
  }

  it should "have a different nonce when new orders are added" in {
    val positions = PeerPositions(market, Seq(randomBid()))
    val newPositions = positions.addEntry(randomBid())
    newPositions.nonce should not equal positions.nonce
  }

  private def randomBid() = {
    OrderBookEntry(OrderId.random(), Bid, 1.BTC, Price(400.EUR))
  }
}
