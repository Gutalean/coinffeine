package coinffeine.model.market

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.network.PeerId

class PositionTest extends UnitTest {

  private val id = PositionId(PeerId.hashOf("user"), OrderId("order"))
  private val price = Price(100.EUR)

  "A position" should "have a positive amount" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      Position.bid(Bitcoin.Zero, price, id)
    }
  }

  it should "have a positive handshaking amount" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      Position.bid(Bitcoin.Zero, price, id).clearHandshake(1.BTC)
    }
  }

  it should "have a handshaking amount not bigger than the whole bitcoin amount" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      Position.bid(1.BTC, price, id).startHandshake(2.BTC)
    }
  }

  it should "be folded depending on its type" in  {
    Position.bid(1.BTC, price, id).fold(
      bid = p => p.amount,
      ask = p => -p.amount
    ) should be (1.BTC)
    Position.ask(1.BTC, price, id).fold(
      bid = p => p.amount,
      ask = p => -p.amount
    ) should be (-1.BTC)
  }
}
