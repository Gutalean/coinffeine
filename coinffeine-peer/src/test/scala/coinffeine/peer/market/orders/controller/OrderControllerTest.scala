package coinffeine.peer.market.orders.controller

import org.mockito.Mockito.{times, verify}
import org.scalatest.mock.MockitoSugar

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.BlockedCoinsId
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.model.payment.OkPayPaymentProcessor
import coinffeine.model.payment.PaymentProcessor.BlockedFundsId
import coinffeine.peer.amounts.DefaultAmountsComponent
import coinffeine.peer.exchange.protocol.MockExchangeProtocol
import coinffeine.protocol.messages.brokerage.OrderMatch

class OrderControllerTest extends UnitTest with MockitoSugar with SampleExchange {

  val initialOrder = Order(Bid, 10.BTC, Price(1.EUR))
  val blockedFunds = Exchange.BlockedFunds(Some(BlockedFundsId(1)), BlockedCoinsId(1))
  val orderMatch = OrderMatch(
    orderId = initialOrder.id,
    exchangeId = ExchangeId.random(),
    bitcoinAmount = Both(buyer = 10.BTC, seller = 10.0003.BTC),
    fiatAmount = Both(buyer = 10.EUR, seller = OkPayPaymentProcessor.amountMinusFee(10.EUR)),
    lockTime = 80L,
    counterpart = PeerId("counterpart")
  )

  "A mutable order" should "start new exchanges" in new Fixture {
    funds.blockFunds(blockedFunds)
    funds.makeAvailable()
    val MatchAccepted(newExchange) = order.acceptOrderMatch(orderMatch)
    order.view.exchanges should have size 1
    newExchange.blockedFunds shouldBe blockedFunds
    newExchange.amounts shouldBe amountsCalculator.exchangeAmountsFor(orderMatch)
    newExchange.role shouldBe BuyerRole
  }

  it should "notify order state changes" in new Fixture {
    funds.blockFunds(blockedFunds)
    funds.makeAvailable()
    verify(listener).onStatusChanged(StalledOrder("Blocking funds"), OfflineOrder)

    publisher.expectUnsuccessfulPublication()
    verify(listener).onStatusChanged(OfflineOrder, OfflineOrder)

    publisher.expectSuccessfulPublication()
    verify(listener).onStatusChanged(OfflineOrder, InMarketOrder)

    funds.makeUnavailable()
    verify(listener).onStatusChanged(InMarketOrder, OfflineOrder)
    verify(listener).onStatusChanged(OfflineOrder, StalledOrder("No funds available"))
    publisher.isInMarket shouldBe false

    funds.makeAvailable()
    verify(listener).onStatusChanged(StalledOrder("No funds available"), OfflineOrder)

    publisher.expectSuccessfulPublication()
    verify(listener, times(2)).onStatusChanged(OfflineOrder, InMarketOrder)

    order.acceptOrderMatch(orderMatch)
    verify(listener).onStatusChanged(OfflineOrder, InProgressOrder)

    order.completeExchange(complete(order.view.exchanges.values.head))
    verify(listener).onStatusChanged(InProgressOrder, CompletedOrder)
  }

  it should "publish orders while having funds to back them" in new Fixture {
    funds.blockFunds(blockedFunds)
    funds.makeAvailable()
    publisher.expectSuccessfulPublication()
    funds.makeUnavailable()
    publisher should not be 'inMarket
    funds.makeAvailable()
    publisher.expectSuccessfulPublication()
    order.acceptOrderMatch(orderMatch)
    publisher should not be 'inMarket
  }

  it should "stop publishing orders upon cancellation" in new Fixture {
    funds.blockFunds(blockedFunds)
    funds.makeAvailable()
    publisher.expectSuccessfulPublication()
    order.cancel("not needed anymore")
    publisher should not be 'inMarket
  }

  it should "notify successful termination" in new Fixture {
    funds.blockFunds(blockedFunds)
    funds.makeAvailable()
    order.acceptOrderMatch(orderMatch)
    order.completeExchange(complete(order.view.exchanges.values.head))
    verify(listener).onFinish(CompletedOrder)
  }

  it should "notify termination upon cancellation" in new Fixture {
    val cancellationReason = "for the fun of it"
    order.cancel(cancellationReason)
    verify(listener).onStatusChanged(StalledOrder("Blocking funds"), CancelledOrder(cancellationReason))
    verify(listener).onFinish(CancelledOrder(cancellationReason))
  }

  it should "reject order matches when stalled before blocking funds" in new Fixture {
    order.acceptOrderMatch(orderMatch) shouldBe MatchRejected("Blocking funds")
  }

  it should "reject order matches when stalled because funds become unavailable" in new Fixture {
    funds.blockFunds(blockedFunds)
    funds.makeAvailable()
    funds.makeUnavailable()
    order.acceptOrderMatch(orderMatch) shouldBe MatchRejected("No funds available")
  }

  it should "reject order matches during other exchange" in new Fixture {
    funds.blockFunds(blockedFunds)
    funds.makeAvailable()
    order.acceptOrderMatch(orderMatch)
    val otherOrderMatch = orderMatch.copy[Euro.type](exchangeId = ExchangeId("other"))
    order.acceptOrderMatch(otherOrderMatch) shouldBe
      MatchRejected("Exchange already in progress")
  }

  it should "recognize already accepted matches" in new Fixture {
    funds.blockFunds(blockedFunds)
    funds.makeAvailable()
    val MatchAccepted(exchange) = order.acceptOrderMatch(orderMatch)
    order.acceptOrderMatch(orderMatch) shouldBe MatchAlreadyAccepted(exchange)
  }

  it should "reject order matches when order is finished" in new Fixture {
    order.cancel("finished")
    order.acceptOrderMatch(orderMatch) shouldBe MatchRejected("Order already finished")
  }

  it should "support partial matching" in new Fixture {
    val firstHalfMatch, secondHalfMatch = orderMatch.copy(
      exchangeId = ExchangeId.random(),
      bitcoinAmount = Both(buyer = 5.BTC, seller = 5.0003.BTC),
      fiatAmount = Both(buyer = 5.EUR, seller = OkPayPaymentProcessor.amountMinusFee(5.EUR))
    )
    funds.blockFunds(blockedFunds)
    funds.makeAvailable()
    publisher.amountToPublish shouldBe initialOrder.amount

    publisher.expectSuccessfulPublication()
    order.acceptOrderMatch(firstHalfMatch)
    order.completeExchange(complete(order.view.exchanges.values.last))
    verify(listener).onStatusChanged(InProgressOrder, OfflineOrder)
    publisher.amountToPublish shouldBe (initialOrder.amount / 2)

    publisher.expectSuccessfulPublication()
    order.acceptOrderMatch(secondHalfMatch)
    order.completeExchange(complete(order.view.exchanges.values.last))
    verify(listener).onStatusChanged(InProgressOrder, CompletedOrder)
    publisher should not be 'inMarket
  }

  trait Fixture extends DefaultAmountsComponent {
    val listener = mock[OrderController.Listener[Euro.type]]
    val publisher = new MockPublication[Euro.type]
    val funds = new FakeOrderFunds
    val order = new OrderController[Euro.type](
      amountsCalculator, CoinffeineUnitTestNetwork, initialOrder, publisher, funds)
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
