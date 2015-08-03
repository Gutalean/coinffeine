package coinffeine.gui.control

import org.joda.time.DateTime

import coinffeine.common.test.UnitTest
import coinffeine.gui.control.OrderStatusWidget._
import coinffeine.model.bitcoin
import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange.PeerInfo
import coinffeine.model.exchange._
import coinffeine.model.network.PeerId
import coinffeine.model.order.{ActiveOrder, Ask}
import coinffeine.peer.amounts.DefaultAmountsCalculator
import coinffeine.peer.exchange.protocol.FakeExchangeProtocol

class OrderStatusWidgetTest extends UnitTest with SampleExchange {

  "Order status" should "be offline for not started offline orders" in {
    Status.fromOrder(randomOrder(1.BTC)) shouldBe Offline
  }

  it should "be in market for not started in market orders" in {
    Status.fromOrder(randomOrder(1.BTC).becomeInMarket) shouldBe InMarket
  }

  it should "be matching for orders with handshaking exchanges" in {
    val order = randomOrder(1.BTC).withExchange(randomExchange(0.5.BTC))
    Status.fromOrder(order) shouldBe Matching
  }

  it should "be in progress for orders with any exchange making progress" in {
    val order =randomOrder(2.BTC)
      .withExchange(randomWaitingForConfirmationExchange(1.BTC))
      .withExchange(randomInProgressExchange(0.5.BTC, completedSteps = 2))
    Status.fromOrder(order) shouldBe InProgress
  }

  it should "be in market for online orders with completed exchanges but remaining amount to exchange" in {
    val order = randomOrder(1.BTC)
      .withExchange(randomCompletedExchange(0.5.BTC))
      .becomeInMarket
    Status.fromOrder(order) shouldBe InMarket
  }

  it should "be in market for offline orders with completed exchanges but remaining amount to exchange" in {
    val order = randomOrder(1.BTC)
      .withExchange(randomCompletedExchange(0.5.BTC))
      .becomeOffline
    Status.fromOrder(order) shouldBe Offline
  }

  it should "be completed for successfully completed orders" in {
    val order = randomOrder(1.BTC).withExchange(randomCompletedExchange(1.BTC))
    Status.fromOrder(order) shouldBe Completed
  }

  it should "be completed for cancelled orders" in {
    Status.fromOrder(randomOrder(1.BTC).cancel(DateTime.now())) shouldBe Completed
  }

  it should "be waiting for confirmation if all active exchanges are also doing so" in {
    val order = randomOrder(2.BTC)
      .withExchange(randomCompletedExchange(1.BTC))
      .withExchange(randomWaitingForConfirmationExchange(1.BTC))
    Status.fromOrder(order) shouldBe WaitingForConfirmation
  }

  private def randomOrder(amount: BitcoinAmount): ActiveOrder =
    ActiveOrder.randomMarketPrice(Ask, amount, Euro)

  private def randomExchange(amount: BitcoinAmount): HandshakingExchange =
    ActiveExchange.create(
      id = ExchangeId.random(),
      role = SellerRole,
      counterpartId = PeerId.random(),
      amounts = new DefaultAmountsCalculator().exchangeAmountsFor(amount, 100.EUR),
      parameters = parameters,
      createdOn = DateTime.now()
    )

  private def randomWaitingForConfirmationExchange(amount: BitcoinAmount): RunningExchange = {
    val exchange = randomInProgressExchange(amount)
    exchange.completeStep(exchange.amounts.intermediateSteps.size)
  }

  private def randomInProgressExchange(
      amount: BitcoinAmount, completedSteps: Int): RunningExchange =
    randomInProgressExchange(amount).completeStep(completedSteps)

  private def randomInProgressExchange(amount: BitcoinAmount): RunningExchange =
    randomExchange(amount)
      .handshake(
        user = PeerInfo("userAccount", new bitcoin.PublicKey),
        counterpart = PeerInfo("counterpartAccount", new bitcoin.PublicKey),
        timestamp = DateTime.now())
      .startExchanging(FakeExchangeProtocol.DummyDeposits, DateTime.now())

  private def randomCompletedExchange(amount: BitcoinAmount): SuccessfulExchange =
    randomInProgressExchange(amount).complete(DateTime.now())
}
