package coinffeine.model.order

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._

class PriceTest extends UnitTest {

  "A price" should "be strictly positive" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      Price(-1, Euro)
    }
  }

  it should "be averaged with other prices" in {
    Price(10, Euro).averageWith(Price(3, Euro)) shouldBe Price(6.5, Euro)
  }

  it should "be applied to a bitcoin amount" in {
    val price = Price(1000, Euro)
    price.of(0.5.BTC) shouldBe 500.EUR
  }

  it should "be rounded when applied to a bitcoin amount" in {
    val price = Price(10, Euro)
    price.of(0.3333.BTC) shouldBe 3.33.EUR
    price.of(0.6666.BTC) shouldBe 6.67.EUR
  }

  it should "never be rounded to zero when applied to a bitcoin amount" in {
    val price = Price(1, Euro)
    price.of(0.00001.BTC) shouldBe 0.01.EUR
  }
}
