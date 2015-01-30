package coinffeine.model.order

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.market._

class OrderBookEntryTest extends UnitTest {

  "An order" should "correspond to a non-negative amount" in {
    val ex = the [IllegalArgumentException] thrownBy {
      OrderBookEntry.random(Bid, 0 BTC, Price(550 EUR))
    }
    ex.getMessage should include ("Amount ordered must be strictly positive")
  }

  it should "have non-negative price" in {
    val ex = the [IllegalArgumentException] thrownBy {
      OrderBookEntry.random(Bid, 10 BTC, Price(0 EUR))
    }
    ex.getMessage should include ("Price must be strictly positive")
  }

  "A bid" should "be sorted only by decreasing price" in {
    val entryHalfAt980 = OrderBookEntry.random(Bid, 0.5 BTC, Price(980 EUR))
    val entry10At950 = OrderBookEntry.random(Bid, 10 BTC, Price(950 EUR))
    val entry1At950 = OrderBookEntry.random(Bid, 1 BTC, Price(950 EUR))
    Seq(
      entryHalfAt980,
      entry10At950,
      entryHalfAt980,
      entry1At950
    ).sorted(OrderBookEntry.ordering(Euro)) shouldBe Seq(
      entryHalfAt980,
      entryHalfAt980,
      entry10At950,
      entry1At950
    )
  }

  it should "sort market price entries first" in {
    val entry1At100 = OrderBookEntry.random(Bid, 1 BTC, LimitPrice(100 EUR))
    val entry1At200 = OrderBookEntry.random(Bid, 1 BTC, LimitPrice(200 EUR))
    val entry1AtMarket = OrderBookEntry.random(Bid, 1 BTC, MarketPrice(Euro))
    val entryHalfAtMarket = OrderBookEntry.random(Bid, 0.5 BTC, MarketPrice(Euro))
    Seq(
      entryHalfAtMarket,
      entry1At100,
      entry1AtMarket,
      entry1At200
    ).sorted(OrderBookEntry.ordering(Euro)) shouldBe Seq(
      entryHalfAtMarket,
      entry1AtMarket,
      entry1At200,
      entry1At100
    )
  }

  "An ask" should "be sorted only by increasing price" in {
    val entryHalfAt930 = OrderBookEntry.random(Ask, 0.5 BTC, Price(930 EUR))
    val entry10At940 = OrderBookEntry.random(Ask, 10 BTC, Price(940 EUR))
    val entry1At940 = OrderBookEntry.random(Ask, 1 BTC, Price(940 EUR))
    Seq(
      entryHalfAt930,
      entry10At940,
      entryHalfAt930,
      entry1At940
    ).sorted(OrderBookEntry.ordering(Euro)) shouldBe Seq(
      entryHalfAt930,
      entryHalfAt930,
      entry10At940,
      entry1At940
    )
  }

  it should "sort market price entries first" in {
    val entry1At100 = OrderBookEntry.random(Ask, 1 BTC, LimitPrice(100 EUR))
    val entry1At200 = OrderBookEntry.random(Ask, 1 BTC, LimitPrice(200 EUR))
    val entry1AtMarket = OrderBookEntry.random(Ask, 1 BTC, MarketPrice(Euro))
    val entryHalfAtMarket = OrderBookEntry.random(Ask, 0.5 BTC, MarketPrice(Euro))
    Seq(
      entryHalfAtMarket,
      entry1At200,
      entry1AtMarket,
      entry1At100
    ).sorted(OrderBookEntry.ordering(Euro)) shouldBe Seq(
      entryHalfAtMarket,
      entry1AtMarket,
      entry1At100,
      entry1At200
    )
  }
}
