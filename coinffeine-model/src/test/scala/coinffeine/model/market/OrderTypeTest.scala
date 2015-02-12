package coinffeine.model.market

import scala.util.Random

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._

class OrderTypeTest extends UnitTest {

  "Bid order type" should "order prices in descending order" in {
    val prices = Seq[OrderPrice[Euro.type]](
      MarketPrice(Euro),
      MarketPrice(Euro),
      LimitPrice(100.EUR),
      LimitPrice(20.EUR),
      LimitPrice(10.EUR)
    )
    Random.shuffle(prices).sorted(Bid.priceOrdering[Euro.type]) shouldBe prices
  }

  "Ask order type" should "order prices in ascending order" in {
    val prices = Seq[OrderPrice[Euro.type]](
      MarketPrice(Euro),
      MarketPrice(Euro),
      LimitPrice(10.EUR),
      LimitPrice(20.EUR),
      LimitPrice(100.EUR)
    )
    Random.shuffle(prices).sorted(Ask.priceOrdering[Euro.type]) shouldBe prices
  }
}
