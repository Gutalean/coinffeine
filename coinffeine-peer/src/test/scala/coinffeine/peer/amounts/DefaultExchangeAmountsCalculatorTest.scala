package coinffeine.peer.amounts

import coinffeine.common.test.UnitTest
import coinffeine.model.currency.Implicits._
import coinffeine.model.market.{Ask, Bid, Order}

class DefaultExchangeAmountsCalculatorTest extends UnitTest {

  "A bid order" must "need a deposit in BTC plus fees and the amount plus fees in FIAT" in
    new Fixture {
    instance.amountsFor(bidOrder) should be (1005.00.EUR, 0.2.BTC)
  }

  "An ask order" must "need the amount and deposit in BTC plus fees and zero in FIAT" in
    new Fixture {
      instance.amountsFor(askOrder) should be (0.EUR, 1.1.BTC)
  }

  trait Fixture {
    val bitcoinAmount = 1.BTC
    val price = 1000.EUR
    val instance = new DefaultExchangeAmountsCalculator()
    val bidOrder = Order(Bid, bitcoinAmount, price)
    val askOrder = Order(Ask, bitcoinAmount, price)
  }
}
