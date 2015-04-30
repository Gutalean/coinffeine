package coinffeine.gui.application.operations

import scalaz.syntax.std.option._

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.market.{Spread, Market, Price}
import coinffeine.protocol.messages.brokerage.Quote

class OperationsControlPaneTest extends UnitTest {

  val market = Market(Euro)

  "A quote" should "be summarized as the last price when available" in {
    OperationsControlPane.summarize(
      Quote(spread = 205.EUR -> 210.EUR, lastPrice = 206.EUR)) shouldBe Price(206.EUR).some
    OperationsControlPane.summarize(
      Quote(market, lastPrice = Price(206.EUR).some)) shouldBe Price(206.EUR).some
  }

  it should "be summarized with the mean spread price when no last price is available" in {
    OperationsControlPane.summarize(
      Quote(market, spread = Spread(Price(205.EUR), Price(210.EUR)))) shouldBe Price(207.5.EUR).some
  }

  it should "be summarized as the highest bid if no more information is available" in {
    OperationsControlPane.summarize(Quote(market,
      Spread(highestBid = Price(205.EUR).some, lowestAsk = None))) shouldBe Price(205.EUR).some
  }

  it should "be summarized as the lowest ask if no more information is available" in {
    OperationsControlPane.summarize(Quote(market,
      Spread(highestBid = None, lowestAsk = Price(210.EUR).some))) shouldBe Price(210.EUR).some
  }
}
