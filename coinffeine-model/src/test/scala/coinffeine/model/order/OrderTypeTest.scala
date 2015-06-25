package coinffeine.model.order

import scala.util.Random

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._

class OrderTypeTest extends UnitTest {

  "Order type" should "parse from string" in {
    OrderType.parse("bid") shouldBe Some(Bid)
    OrderType.parse("ask") shouldBe Some(Ask)
  }

  it should "fail to parse from invalid string" in {
    OrderType.parse("compra") shouldBe None
  }

  "Bid order type" should "order prices in descending order" in {
    val prices = Seq[OrderPrice](
      MarketPrice(Euro),
      MarketPrice(Euro),
      LimitPrice(100.EUR),
      LimitPrice(20.EUR),
      LimitPrice(10.EUR)
    )
    Random.shuffle(prices).sorted(Bid.priceOrdering) shouldBe prices
  }

  "Ask order type" should "order prices in ascending order" in {
    val prices = Seq[OrderPrice](
      MarketPrice(Euro),
      MarketPrice(Euro),
      LimitPrice(10.EUR),
      LimitPrice(20.EUR),
      LimitPrice(100.EUR)
    )
    Random.shuffle(prices).sorted(Ask.priceOrdering) shouldBe prices
  }
}
