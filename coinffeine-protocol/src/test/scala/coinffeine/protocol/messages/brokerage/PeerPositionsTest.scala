package coinffeine.protocol.messages.brokerage

import coinffeine.common.test.UnitTest
import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.Implicits._
import coinffeine.model.market.{Ask, Bid, OrderBookEntry, OrderId}

class PeerPositionsTest extends UnitTest {

  "Peer orders" should "have the same currency" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      PeerPositions(Market(Euro), Seq(OrderBookEntry(OrderId.random(), Bid, 1.BTC, 400.USD)))
    }
  }

  it should "create a different nonce for each positions object" in {
    val pos1 = PeerPositions(
      Market(Euro), Seq(OrderBookEntry(OrderId.random(), Bid, 1.BTC, 400.EUR)))
    val pos2 = PeerPositions(
      Market(Euro), Seq(OrderBookEntry(OrderId.random(), Bid, 1.BTC, 400.EUR)))
    pos1.nonce should not equal pos2.nonce
  }

  it should "have a different nonce when new orders are added" in {
    val positions = PeerPositions(
      Market(Euro), Seq(OrderBookEntry(OrderId.random(), Bid, 1.BTC, 400.EUR)))
    val newPositions = positions.addEntry(OrderBookEntry(OrderId.random(), Ask, 2.BTC, 600.EUR))
    newPositions.nonce should not equal positions.nonce
  }
}
