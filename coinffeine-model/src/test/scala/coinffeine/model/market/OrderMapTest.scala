package coinffeine.model.market

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.network.PeerId

class OrderMapTest extends UnitTest {
  val peerA = PeerId.hashOf("peerA")
  val peerB = PeerId.hashOf("peerB")
  val posA1 = PositionId(peerA, OrderId("order1"))
  val posA2 = PositionId(peerA, OrderId("order2"))
  val posB = PositionId(peerB, OrderId("order1"))
  val sampleMap = OrderMap(
    Position.bid(2.BTC, Price(3.EUR), posA1),
    Position.bid(1.BTC, Price(5.EUR), posB),
    Position.bid(1.BTC, Price(3.EUR), posA2)
  )

  "An order map" should "order bid positions by descending price and insertion order" in {
    OrderMap(
      Position.bid(2.BTC, Price(3.EUR), posA1),
      Position.bid(1.BTC, Price(5.EUR), posB),
      Position.bid(1.BTC, Price(3.EUR), posA2)
    ).positions shouldBe Seq(
      Position.bid(1.BTC, Price(5.EUR), posB),
      Position.bid(2.BTC, Price(3.EUR), posA1),
      Position.bid(1.BTC, Price(3.EUR), posA2)
    )
  }

  it should "order ask positions by ascending price and insertion order" in {
    OrderMap(
      Position.ask(2.BTC, Price(3.EUR), posA1),
      Position.ask(1.BTC, Price(5.EUR), posB),
      Position.ask(1.BTC, Price(3.EUR), posA2)
    ).positions shouldBe Seq(
      Position.ask(2.BTC, Price(3.EUR), posA1),
      Position.ask(1.BTC, Price(3.EUR), posA2),
      Position.ask(1.BTC, Price(5.EUR), posB)
    )
  }

  it should "cancel a position by position id" in {
    sampleMap.cancelPosition(posB) shouldBe OrderMap(
      Position.bid(2.BTC, Price(3.EUR), posA1),
      Position.bid(1.BTC, Price(3.EUR), posA2)
    )
  }

  it should "decrease a position amount" in {
    val updatedMap = sampleMap.decreaseAmount(posA1, 1.BTC)
    updatedMap shouldBe OrderMap(
      Position.bid(1.BTC, Price(3.EUR), posA1),
      Position.bid(1.BTC, Price(5.EUR), posB),
      Position.bid(1.BTC, Price(3.EUR), posA2)
    )
  }
}
