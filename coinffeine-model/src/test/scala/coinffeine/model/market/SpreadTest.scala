package coinffeine.model.market

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.order.Price

class SpreadTest extends UnitTest {

  "A price spread" should "be converted to string" in {
    Spread.empty.toString shouldBe "(--, --)"
    Spread(Some(Price(10.USD)), lowestAsk = None).toString shouldBe "(10 USD, --)"
    Spread(Price(10.785, Euro), Price(10.896, Euro)).toString shouldBe "(10.785 EUR, 10.896 EUR)"
  }

  it should "be crossed when highest bid outbids lower ask" in {
    Spread(Price(100, Euro), Price(300, Euro)) should not be 'crossed
    Spread(Price(100, Euro), Price(100, Euro)) shouldBe 'crossed
    Spread(Price(150, Euro), Price(100, Euro)) shouldBe 'crossed
  }
}
