package coinffeine.model.order

import org.joda.time.DateTime

import coinffeine.common.test.UnitTest
import coinffeine.model.Both
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction}
import coinffeine.model.currency._
import coinffeine.model.exchange.ActiveExchange.Parameters
import coinffeine.model.exchange._

class ActiveOrderTest extends UnitTest with SampleExchange with CoinffeineUnitTestNetwork.Component {

  val exchangeParameters = Parameters(10, network)
  val dummyDeposits = Both.fill(ImmutableTransaction(new MutableTransaction(network)))

  "An order" must "require its amount to be strictly positive" in {
    an [IllegalArgumentException] shouldBe thrownBy { ActiveOrder.randomMarketPrice(Bid, 0.BTC, Euro) }
    an [IllegalArgumentException] shouldBe thrownBy { ActiveOrder.randomLimit(Bid, -1.BTC, Price(1.EUR)) }
  }

  it must "report no progress with no exchanges" in {
    ActiveOrder.randomLimit(Bid, 10.BTC, Price(10.EUR)).progress shouldBe 0.0
  }

  it must "report progress with one incomplete exchange" in {
    val order = ActiveOrder.randomLimit(Bid, 10.BTC, Price(10.EUR)).withExchange(createExchangeInProgress(5))
    order.progress shouldBe 0.5
  }

  it must "report progress with one incomplete exchange that overwrites itself" in {
    val initialOrder = ActiveOrder.randomLimit(Bid, 10.BTC, Price(10.EUR))
    val exchange = createExchangeInProgress(5)
    val order = initialOrder
      .withExchange(exchange)
      .withExchange(exchange.completeStep(6))
    order.progress shouldBe 0.6
  }

  it must "report progress with a mixture of completed and incomplete exchanges" in {
    val order = ActiveOrder.randomLimit(Bid, 20.BTC, Price(10.EUR))
      .withExchange(createSuccessfulExchange())
      .withExchange(createExchangeInProgress(5))
    order.progress shouldBe 0.75
  }

  it must "consider failed exchange progress" in {
    val order = ActiveOrder.randomLimit(Bid, 20.BTC, Price(10.EUR))
      .withExchange(createSuccessfulExchange())
      .withExchange(createExchangeInProgress(5).stepFailure(
        step = 5,
        transaction = None,
        timestamp = DateTime.now()
      ))
    order.progress shouldBe 0.75
  }

  it must "have its amount pending at the start" in {
    val order = ActiveOrder.randomLimit(Bid, 10.BTC, Price(1.EUR))
    order.amounts shouldBe ActiveOrder.Amounts(exchanged = 0.BTC, exchanging = 0.BTC, pending = 10.BTC)
  }

  it must "consider successfully exchanged amounts" in {
    val order = ActiveOrder.randomLimit(Bid, 100.BTC, Price(1.EUR))
      .withExchange(createSuccessfulExchange())
      .withExchange(createSuccessfulExchange())
    order.amounts shouldBe ActiveOrder.Amounts(exchanged = 20.BTC, exchanging = 0.BTC, pending = 80.BTC)
  }

  it must "consider in-progress exchange amounts" in {
    val order = ActiveOrder.randomLimit(Bid, 100.BTC, Price(1.EUR))
      .withExchange(createSuccessfulExchange())
      .withExchange(createExchangeInProgress(5))
    order.amounts shouldBe ActiveOrder.Amounts(exchanged = 10.BTC, exchanging = 10.BTC, pending = 80.BTC)
    order.status shouldBe OrderStatus.InProgress
  }

  it must "consider failed exchange amounts" in {
    val order = ActiveOrder.randomLimit(Bid, 100.BTC, Price(1.EUR))
      .withExchange(createSuccessfulExchange())
      .withExchange(createExchangeInProgress(5).stepFailure(
        step = 5,
        transaction = None,
        timestamp = DateTime.now()
      ))
    order.amounts shouldBe ActiveOrder.Amounts(exchanged = 15.BTC, exchanging = 0.BTC, pending = 85.BTC)
    order.status shouldBe OrderStatus.InProgress
  }

  it must "detect completion when exchanges complete the order" in {
    val order = ActiveOrder.randomLimit(Bid, 20.BTC, Price(1.EUR))
      .withExchange(createSuccessfulExchange())
      .withExchange(createSuccessfulExchange())
    order.status shouldBe OrderStatus.Completed
  }

  it must "become offline when the pending amount changes" in {
    val order = ActiveOrder.randomLimit(Bid, 20.BTC, Price(1.EUR))
      .becomeInMarket
      .withExchange(createSuccessfulExchange())
    order should not be 'inMarket
  }

  it must "be in market when there is pending amount to be exchanged" in {
    ActiveOrder.randomLimit(Bid, 10.BTC, Price(1.EUR)) shouldBe 'shouldBeOnMarket
  }

  it must "not be in market when the exchange is finished" in {
    val order = ActiveOrder.randomLimit(Bid, 10.BTC, Price(1.EUR))
    order.cancel(DateTime.now()) should not be 'shouldBeOnMarket
    order.withExchange(createSuccessfulExchange()) should not be 'shouldBeOnMarket
  }

  it must "be in market despite an exchange is running" in {
    ActiveOrder.randomLimit(Bid, 20.BTC, Price(1.EUR))
      .withExchange(createExchangeInProgress(5)) shouldBe 'shouldBeOnMarket
  }

  it must "have the timestamp of the most recent change" in {
    val createdOn = DateTime.now().minusMinutes(10)
    val matchedOn = createdOn.plusMinutes(1)
    val order = ActiveOrder.randomLimit(Bid, 20.BTC, Price(1.EUR), createdOn)
    order.lastChange shouldBe createdOn
    order.withExchange(createRandomExchange(matchedOn)).lastChange shouldBe matchedOn
  }

  it must "be cancellable right away in absence of exchanges" in {
    val order = ActiveOrder.randomLimit(Bid, 11.BTC, Price(1.EUR))
    order shouldBe 'cancellable
    order.cancel(DateTime.now()).status shouldBe OrderStatus.Cancelled
  }

  it must "be cancellable right away if existing exchanges are finished" in {
    val order = ActiveOrder.randomLimit(Bid, 11.BTC, Price(1.EUR))
      .withExchange(createSuccessfulExchange())
    order shouldBe 'cancellable
    order.cancel(DateTime.now()).status shouldBe OrderStatus.Cancelled
  }

  it must "fail to be cancelled if there are active exchanges" in {
    val order = ActiveOrder.randomLimit(Bid, 11.BTC, Price(1.EUR))
      .withExchange(createExchangeInProgress(10))
    order should not be 'cancellable
    an [IllegalArgumentException] shouldBe thrownBy { order.cancel(DateTime.now()) }
  }

  it must "fail to be cancelled if already completed" in {
    val order = ActiveOrder.randomLimit(Bid, 10.BTC, Price(1.EUR))
      .withExchange(createSuccessfulExchange())
    order.status shouldBe OrderStatus.Completed
    order should not be 'cancellable
    an [IllegalArgumentException] shouldBe thrownBy { order.cancel(DateTime.now()) }
  }

  private def createSuccessfulExchange() = createExchangeInProgress(10).complete(DateTime.now())

  private def createExchangeInProgress(stepsCompleted: Int) = {
    createRandomExchange()
      .handshake(participants.buyer, participants.seller, DateTime.now())
      .startExchanging(dummyDeposits, DateTime.now())
      .completeStep(stepsCompleted)
  }

  private def createRandomExchange(
      timestamp: DateTime = DateTime.now()): HandshakingExchange =
    ActiveExchange.create(
      ExchangeId.random(), BuyerRole, peerIds.seller, amounts, parameters, timestamp)
}
