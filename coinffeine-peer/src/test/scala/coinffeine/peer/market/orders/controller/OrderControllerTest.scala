package coinffeine.peer.market.orders.controller

import org.scalatest.Inside
import org.scalatest.mock.MockitoSugar

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.currency._
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.model.payment.OkPayPaymentProcessor
import coinffeine.peer.amounts.DefaultAmountsComponent
import coinffeine.peer.exchange.protocol.MockExchangeProtocol
import coinffeine.protocol.messages.brokerage.OrderMatch

class OrderControllerTest extends UnitTest with Inside with MockitoSugar with SampleExchange {

  val initialOrder = Order(Bid, 10.BTC, Price(1.EUR))
  val orderMatch = OrderMatch(
    orderId = initialOrder.id,
    exchangeId = ExchangeId.random(),
    bitcoinAmount = Both(buyer = 10.BTC, seller = 10.0003.BTC),
    fiatAmount = Both(buyer = 10.EUR, seller = OkPayPaymentProcessor.amountMinusFee(10.EUR)),
    lockTime = 80L,
    counterpart = PeerId("counterpart")
  )

  "A mutable order" should "start new exchanges" in new Fixture {
    order.acceptOrderMatch(orderMatch)
    fundRequests.successfullyBlockFunds()
    val MatchAccepted(newExchange) = listener.lastMatchResolution
    order.view.exchanges should have size 1
    newExchange.amounts shouldBe amountsCalculator.exchangeAmountsFor(orderMatch)
    newExchange.role shouldBe BuyerRole
  }

  it should "notify order state changes" in new Fixture {
    publisher.expectUnsuccessfulPublication()
    listener.lastStatus shouldBe OfflineOrder

    publisher.expectSuccessfulPublication()
    listener.lastStatus shouldBe InMarketOrder

    order.acceptOrderMatch(orderMatch)
    fundRequests.successfullyBlockFunds()
    listener.lastStatus shouldBe InProgressOrder

    order.completeExchange(complete(order.view.exchanges.values.head))
    listener.lastStatus shouldBe CompletedOrder
  }

  it should "stop publishing orders upon cancellation" in new Fixture {
    publisher.expectSuccessfulPublication()
    order.cancel("not needed anymore")
    publisher should not be 'inMarket
  }

  it should "notify successful termination" in new Fixture {
    order.acceptOrderMatch(orderMatch)
    fundRequests.successfullyBlockFunds()
    order.completeExchange(complete(order.view.exchanges.values.head))
    listener.lastStatus shouldBe CompletedOrder
  }

  it should "notify termination upon cancellation" in new Fixture {
    val cancellationReason = "for the fun of it"
    order.cancel(cancellationReason)
    listener.lastStatus shouldBe CancelledOrder(cancellationReason)
  }

  it should "reject order matches when blocking funds for an exchange" in new Fixture {
    order.acceptOrderMatch(orderMatch)
    order.acceptOrderMatch(orderMatch.copy(exchangeId = ExchangeId.random()))
    listener.lastMatchResolution shouldBe MatchRejected("Accepting other match")
  }

  it should "reject order matches during other exchange" in new Fixture {
    order.acceptOrderMatch(orderMatch)
    fundRequests.successfullyBlockFunds()
    order.acceptOrderMatch(orderMatch.copy(exchangeId = ExchangeId("other")))
    inside(listener.orderMatchResolutions) {
      case Seq(MatchAccepted(_), MatchRejected("Exchange already in progress")) =>
    }
  }

  it should "recognize already accepted matches" in new Fixture {
    order.acceptOrderMatch(orderMatch)
    fundRequests.successfullyBlockFunds()
    order.acceptOrderMatch(orderMatch)
    inside(listener.orderMatchResolutions) {
      case Seq(MatchAccepted(exchangeAccepted), MatchAlreadyAccepted(exchangeInProgress)) =>
        exchangeAccepted shouldBe exchangeInProgress
    }
  }

  it should "reject order matches when order is finished" in new Fixture {
    order.cancel("finished")
    order.acceptOrderMatch(orderMatch)
    listener.lastMatchResolution shouldBe MatchRejected("Order already finished")
  }

  it should "support partial matching" in new Fixture {
    val firstHalfMatch, secondHalfMatch = orderMatch.copy(
      exchangeId = ExchangeId.random(),
      bitcoinAmount = Both(buyer = 5.BTC, seller = 5.0003.BTC),
      fiatAmount = Both(buyer = 5.EUR, seller = OkPayPaymentProcessor.amountMinusFee(5.EUR))
    )
    publisher.amountToPublish shouldBe initialOrder.amount
    publisher.expectSuccessfulPublication()

    order.acceptOrderMatch(firstHalfMatch)
    fundRequests.successfullyBlockFunds()
    order.completeExchange(complete(order.view.exchanges.values.last))
    listener.lastStatus shouldBe OfflineOrder

    publisher.amountToPublish shouldBe (initialOrder.amount / 2)
    publisher.expectSuccessfulPublication()

    order.acceptOrderMatch(secondHalfMatch)
    fundRequests.successfullyBlockFunds()
    order.completeExchange(complete(order.view.exchanges.values.last))
    listener.lastStatus shouldBe CompletedOrder
    publisher should not be 'inMarket
  }

  trait Fixture extends DefaultAmountsComponent {
    val listener = new MockOrderControllerListener[Euro.type]
    val publisher = new MockPublication[Euro.type]
    val fundRequests = new FakeFundsBlocker
    val order = new OrderController[Euro.type](
      amountsCalculator, CoinffeineUnitTestNetwork, initialOrder, publisher, fundRequests)
    order.addListener(listener)

    def complete(exchange: AnyStateExchange[Euro.type]): SuccessfulExchange[Euro.type] = {
      val completedState = Exchange.Successful[Euro.type](
        participants.buyer,
        participants.seller,
        MockExchangeProtocol.DummyDeposits)(exchange.amounts)
      exchange.copy(state = completedState)
    }
  }
}
