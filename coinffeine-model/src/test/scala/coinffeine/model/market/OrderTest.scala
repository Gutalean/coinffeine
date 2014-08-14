package coinffeine.model.market

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction}
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange._

class OrderTest extends UnitTest with SampleExchange with CoinffeineUnitTestNetwork.Component {

  val exchangeParameters = Exchange.Parameters(10, network)
  val dummyDeposits =
    Exchange.Deposits(Both.fill(ImmutableTransaction(new MutableTransaction(network))))

  "Order" must "report no progress with no exchanges" in {
    val order = Order(OrderId.random(), Bid, 10.BTC, 10.EUR)
    order.progress should be (0.0)
  }

  it must "report progress with one incomplete exchange" in {
    val order = Order(OrderId.random(), Bid, 10.BTC, 10.EUR)
      .withExchange(createExchange(0.5))
    order.progress should be (0.5)
  }

  it must "report progress with one incomplete exchange that overwrites itself" in {
    val order = Order(OrderId.random(), Bid, 10.BTC, 10.EUR)
      .withExchange(createExchange(0.5))
      .withExchange(createExchange(0.6))
    order.progress should be (0.6)
  }

  private def createExchange(completion: Double) = buyerHandshakingExchange
    .startExchanging(dummyDeposits)
    .increaseProgress(btcAmount = 10.BTC * completion, fiatAmount = 10.EUR * completion)
}
