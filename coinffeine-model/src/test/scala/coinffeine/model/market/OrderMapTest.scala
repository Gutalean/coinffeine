package coinffeine.model.market

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.network.PeerId
import coinffeine.model.order.{OrderId, Price}

class OrderMapTest extends UnitTest {
  val peerA = PeerId.hashOf("peerA")
  val peerB = PeerId.hashOf("peerB")
  val posA1 = PositionId(peerA, OrderId("order1"))
  val posA2 = PositionId(peerA, OrderId("order2"))
  val posB = PositionId(peerB, OrderId("order1"))
  val sampleMap = OrderMap(
    Position.limitBid(2.BTC, Price(3.EUR), posA1),
    Position.limitBid(1.BTC, Price(5.EUR), posB),
    Position.limitBid(1.BTC, Price(3.EUR), posA2)
  )

  "An order map" should "order bid positions by descending price and insertion order" in {
    OrderMap(
      Position.limitBid(2.BTC, Price(3.EUR), posA1),
      Position.limitBid(1.BTC, Price(5.EUR), posB),
      Position.limitBid(1.BTC, Price(3.EUR), posA2)
    ).positions shouldBe Seq(
      Position.limitBid(1.BTC, Price(5.EUR), posB),
      Position.limitBid(2.BTC, Price(3.EUR), posA1),
      Position.limitBid(1.BTC, Price(3.EUR), posA2)
    )
  }

  it should "order ask positions by ascending price and insertion order" in {
    OrderMap(
      Position.limitAsk(2.BTC, Price(3.EUR), posA1),
      Position.limitAsk(1.BTC, Price(5.EUR), posB),
      Position.limitAsk(1.BTC, Price(3.EUR), posA2)
    ).positions shouldBe Seq(
      Position.limitAsk(2.BTC, Price(3.EUR), posA1),
      Position.limitAsk(1.BTC, Price(3.EUR), posA2),
      Position.limitAsk(1.BTC, Price(5.EUR), posB)
    )
  }

  it should "cancel a position by position id" in {
    sampleMap.cancelPosition(posB) shouldBe OrderMap(
      Position.limitBid(2.BTC, Price(3.EUR), posA1),
      Position.limitBid(1.BTC, Price(3.EUR), posA2)
    )
  }

  it should "decrease a position amount" in {
    val updatedMap = sampleMap.decreaseAmount(posA1, 1.BTC)
    updatedMap shouldBe OrderMap(
      Position.limitBid(1.BTC, Price(3.EUR), posA1),
      Position.limitBid(1.BTC, Price(5.EUR), posB),
      Position.limitBid(1.BTC, Price(3.EUR), posA2)
    )
  }
}
