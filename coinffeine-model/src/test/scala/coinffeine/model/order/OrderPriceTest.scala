package coinffeine.model.order

import scalaz.syntax.std.option._

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._

class OrderPriceTest extends UnitTest {

  val marketEur = MarketPrice(Euro)
  val marketUsd = MarketPrice(UsDollar)
  val limit100 = LimitPrice(100.EUR)
  val limit150 = LimitPrice(150.EUR)

  "An order price" should "know if it is limited" in {
    marketEur should not be 'limited
    limit100 shouldBe 'limited
    limit150 shouldBe 'limited
  }

  it should "know its currency" in {
    marketEur.currency shouldBe Euro
    marketUsd.currency shouldBe UsDollar
  }

  it should "be converted to option" in {
    marketEur.toOption shouldBe 'empty
    limit100.toOption shouldBe Price(100.EUR).some
    limit150.toOption shouldBe Price(150.EUR).some
  }

  it should "know what prices outbid other prices" in {
    marketEur.outbids(marketEur) shouldBe false
    marketEur.outbids(limit150) shouldBe true
    marketEur.outbids(limit100) shouldBe true

    limit100.outbids(marketEur) shouldBe false
    limit100.outbids(limit150) shouldBe false
    limit100.outbids(limit100) shouldBe false

    limit150.outbids(marketEur) shouldBe false
    limit150.outbids(limit150) shouldBe false
    limit150.outbids(limit100) shouldBe true
  }

  it should "know what prices outbid or matches other prices" in {
    marketEur.outbidsOrMatches(marketEur) shouldBe true
    marketEur.outbidsOrMatches(limit150) shouldBe true
    marketEur.outbidsOrMatches(limit100) shouldBe true

    limit100.outbidsOrMatches(marketEur) shouldBe false
    limit100.outbidsOrMatches(limit150) shouldBe false
    limit100.outbidsOrMatches(limit100) shouldBe true

    limit150.outbidsOrMatches(marketEur) shouldBe false
    limit150.outbidsOrMatches(limit150) shouldBe true
    limit150.outbidsOrMatches(limit100) shouldBe true
  }

  it should "know what prices underbid other prices" in {
    marketEur.underbids(marketEur) shouldBe false
    marketEur.underbids(limit150) shouldBe false
    marketEur.underbids(limit100) shouldBe false

    limit100.underbids(marketEur) shouldBe true
    limit100.underbids(limit150) shouldBe true
    limit100.underbids(limit100) shouldBe false

    limit150.underbids(marketEur) shouldBe true
    limit150.underbids(limit150) shouldBe false
    limit150.underbids(limit100) shouldBe false
  }

  it should "know what prices underbid or matches other prices" in {
    marketEur.underbidsOrMatches(marketEur) shouldBe true
    marketEur.underbidsOrMatches(limit150) shouldBe false
    marketEur.underbidsOrMatches(limit100) shouldBe false

    limit100.underbidsOrMatches(marketEur) shouldBe true
    limit100.underbidsOrMatches(limit150) shouldBe true
    limit100.underbidsOrMatches(limit100) shouldBe true

    limit150.underbidsOrMatches(marketEur) shouldBe true
    limit150.underbidsOrMatches(limit150) shouldBe true
    limit150.underbidsOrMatches(limit100) shouldBe false
  }
}
