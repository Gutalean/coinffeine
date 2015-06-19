package coinffeine.gui.control

import org.joda.time.DateTime

import coinffeine.common.test.UnitTest
import coinffeine.gui.control.OrderStatusWidget._
import coinffeine.model.bitcoin.PublicKey
import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange.PeerInfo
import coinffeine.model.exchange._
import coinffeine.model.network.PeerId
import coinffeine.model.order.{ActiveOrder, Ask}
import coinffeine.peer.amounts.DefaultAmountsComponent
import coinffeine.peer.exchange.protocol.FakeExchangeProtocol

class OrderStatusWidgetTest extends UnitTest with SampleExchange with DefaultAmountsComponent {

  "Order status" should "be submitting for not started offline orders" in {
    Status.fromOrder(randomOrder(1.BTC)) shouldBe Submitting
  }

  it should "be in market for not started in market orders" in {
    Status.fromOrder(randomOrder(1.BTC).becomeInMarket) shouldBe InMarket
  }

  it should "be matching for orders with handshaking exchanges" in {
    val order = randomOrder(1.BTC).withExchange(randomExchange(0.5.BTC))
    Status.fromOrder(order) shouldBe Matching
  }

  it should "be in progress for orders with any exchange making progress" in {
    val order = randomOrder(1.BTC).withExchange(randomInProgressExchange(0.5.BTC, 2))
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
    Status.fromOrder(order) shouldBe Submitting
  }

  it should "be completed for successfully completed orders" in {
    val order = randomOrder(1.BTC).withExchange(randomCompletedExchange(1.BTC))
    Status.fromOrder(order) shouldBe Completed
  }

  it should "be completed for cancelled orders" in {
    Status.fromOrder(randomOrder(1.BTC).cancel(DateTime.now())) shouldBe Completed
  }

  private def randomOrder(amount: Bitcoin.Amount): ActiveOrder[Euro.type] = {
    ActiveOrder.randomMarketPrice(Ask, amount, Euro)
  }

  private def randomExchange(amount: Bitcoin.Amount): HandshakingExchange[Euro.type] =
    ActiveExchange.create(
      id = ExchangeId.random(),
      role = SellerRole,
      counterpartId = PeerId.random(),
      amounts = amountsCalculator.exchangeAmountsFor(amount, 100.EUR),
      parameters = parameters,
      createdOn = DateTime.now()
    )

  private def randomInProgressExchange(amount: Bitcoin.Amount,
                                       complededSteps: Int): RunningExchange[Euro.type] =
    randomExchange(amount)
      .handshake(
        user = PeerInfo("userAccount", new PublicKey),
        counterpart = PeerInfo("counterpartAccount", new PublicKey),
        timestamp = DateTime.now())
      .startExchanging(FakeExchangeProtocol.DummyDeposits, DateTime.now())
      .completeStep(complededSteps)

  private def randomCompletedExchange(amount: Bitcoin.Amount): SuccessfulExchange[Euro.type] =
    randomInProgressExchange(amount, 1).complete(DateTime.now())
}
