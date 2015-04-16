package coinffeine.gui.control

import org.joda.time.DateTime

import coinffeine.common.test.UnitTest
import coinffeine.gui.control.OrderStatusWidget._
import coinffeine.model.bitcoin.PublicKey
import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange.PeerInfo
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.peer.amounts.DefaultAmountsComponent
import coinffeine.peer.exchange.protocol.MockExchangeProtocol

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

  it should "be in progress for orders with any order making progress" in {
    val order = randomOrder(1.BTC).withExchange(randomInProgressExchange(0.5.BTC, 2))
    Status.fromOrder(order) shouldBe InProgress
  }

  it should "be matching for orders with completed exchanges but remaining amount to exchange" in {
    val order = randomOrder(1.BTC)
      .withExchange(randomCompletedExchange(0.5.BTC))
      .withExchange(randomExchange(0.5.BTC))
    Status.fromOrder(order) shouldBe Matching
  }

  it should "be completed for successfully completed orders" in {
    val order = randomOrder(1.BTC).withExchange(randomCompletedExchange(1.BTC))
    Status.fromOrder(order) shouldBe Completed
  }

  it should "be completed for cancelled orders" in {
    Status.fromOrder(randomOrder(1.BTC).cancel) shouldBe Completed
  }

  private def randomOrder(amount: Bitcoin.Amount): Order[Euro.type] = {
    Order.randomMarketPrice(Ask, amount, Euro)
  }

  private def randomExchange(amount: Bitcoin.Amount): HandshakingExchange[Euro.type] =
    Exchange.create(
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
      .startExchanging(MockExchangeProtocol.DummyDeposits, DateTime.now())
      .completeStep(complededSteps)

  private def randomCompletedExchange(amount: Bitcoin.Amount): SuccessfulExchange[Euro.type] =
    randomInProgressExchange(amount, 1).complete(DateTime.now())
}
