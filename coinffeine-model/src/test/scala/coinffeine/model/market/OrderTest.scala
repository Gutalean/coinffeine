package coinffeine.model.market

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction}
import coinffeine.model.currency._
import coinffeine.model.exchange._

class OrderTest extends UnitTest with SampleExchange with CoinffeineUnitTestNetwork.Component {

  val exchangeParameters = Exchange.Parameters(10, network)
  val dummyDeposits = Both.fill(ImmutableTransaction(new MutableTransaction(network)))

  "An order" must "report no progress with no exchanges" in {
    Order(Bid, 10.BTC, Price(10.EUR)).progress shouldBe 0.0
  }

  it must "report progress with one incomplete exchange" in {
    val order = Order(Bid, 10.BTC, Price(10.EUR)).withExchange(createExchangeInProgress(5))
    order.progress shouldBe 0.5
  }

  it must "report progress with one incomplete exchange that overwrites itself" in {
    val exchange = createExchangeInProgress(5)
    val order = Order(OrderId.random(), Bid, 10.BTC, Price(10.EUR))
      .withExchange(exchange)
      .withExchange(exchange.completeStep(6))
    order.progress shouldBe 0.6
  }

  it must "report progress with a mixture of completed and incomplete exchanges" in {
    val order = Order(Bid, 20.BTC, Price(10.EUR))
      .withExchange(createSuccessfulExchange())
      .withExchange(createExchangeInProgress(5))
    order.progress shouldBe 0.75
  }

  it must "have its amount pending at the start" in {
    val order = Order(Bid, 10.BTC, Price(1.EUR))
    order.amounts shouldBe Order.Amounts(exchanged = 0.BTC, exchanging = 0.BTC, pending = 10.BTC)
  }

  it must "consider successfully exchanged amounts" in {
    val order = Order(Bid, 100.BTC, Price(1.EUR))
      .withExchange(createSuccessfulExchange())
      .withExchange(createSuccessfulExchange())
    order.amounts shouldBe Order.Amounts(exchanged = 20.BTC, exchanging = 0.BTC, pending = 80.BTC)
  }

  it must "consider in-progress exchange amounts" in {
    val order = Order(Bid, 100.BTC, Price(1.EUR))
      .withExchange(createSuccessfulExchange())
      .withExchange(createExchangeInProgress(5))
    order.amounts shouldBe Order.Amounts(exchanged = 10.BTC, exchanging = 10.BTC, pending = 80.BTC)
  }

  private def createSuccessfulExchange() = createExchangeInProgress(10).complete

  private def createExchangeInProgress(stepsCompleted: Int) = {
    createRandomExchange()
      .startHandshaking(participants.buyer, participants.seller)
      .startExchanging(dummyDeposits)
      .completeStep(stepsCompleted)
  }

  private def createRandomExchange(): NotStartedExchange[Euro.type] = {
    buyerExchange.copy(metadata = buyerExchange.metadata.copy(id = ExchangeId.random()))
  }
}
