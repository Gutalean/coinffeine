package coinffeine.peer.market.orders.controller

import org.scalatest.Inside

import coinffeine.common.test.UnitTest
import coinffeine.model.Both
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.currency._
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.model.order._
import coinffeine.model.payment.OkPayPaymentProcessor
import coinffeine.peer.amounts.DefaultAmountsCalculator
import coinffeine.peer.exchange.protocol.FakeExchangeProtocol
import coinffeine.protocol.messages.brokerage.OrderMatch

class OrderControllerTest extends UnitTest with Inside with SampleExchange {

  private val initialOrder =
    ActiveOrder.randomLimit(Bid, 10.BTC, Price(1.EUR), ExchangeTimestamps.creation.minusMinutes(1))
  private val orderMatch = OrderMatch(
    orderId = initialOrder.id,
    exchangeId = ExchangeId.random(),
    bitcoinAmount = Both(buyer = 10.BTC, seller = 10.0003.BTC),
    fiatAmount = Both(buyer = 10.EUR, seller = OkPayPaymentProcessor.amountMinusFee(10.EUR)),
    lockTime = 80L,
    counterpart = PeerId.hashOf("counterpart")
  )
  private val requiredFunds = RequiredFunds(4.0202005.BTC, 10.EUR)
  private val firstHalfMatch, secondHalfMatch = orderMatch.copy(
    exchangeId = ExchangeId.random(),
    bitcoinAmount = Both(buyer = 5.BTC, seller = 5.0003.BTC),
    fiatAmount = Both(buyer = 5.EUR, seller = OkPayPaymentProcessor.amountMinusFee(5.EUR))
  )
  private val halfRequiredFunds = RequiredFunds(4.0202005.BTC, 5.EUR)

  "An order controller" should "start new exchanges" in new Fixture {
    order.shouldAcceptOrderMatch(orderMatch) shouldBe MatchAccepted(requiredFunds)
    order.fundsRequested(orderMatch, requiredFunds)
    val newExchange = order.startExchange(orderMatch.exchangeId, ExchangeTimestamps.creation)
    newExchange.amounts shouldBe amountsCalculator.exchangeAmountsFor(orderMatch)
    newExchange.role shouldBe BuyerRole
    order.view.exchanges should have size 1
  }

  it should "notify order state changes" in new Fixture {
    order.becomeOffline()
    listener.lastOrder should not be 'inMarket

    order.becomeInMarket()
    listener.lastOrder shouldBe 'inMarket

    order.fundsRequested(orderMatch, requiredFunds)
    order.startExchange(orderMatch.exchangeId, ExchangeTimestamps.creation)
    listener.lastStatus shouldBe OrderStatus.InProgress

    order.completeExchange(complete(order.view.exchanges.values.head))
    listener.lastStatus shouldBe OrderStatus.Completed
  }

  it should "stop publishing orders upon cancellation" in new Fixture {
    order.cancel(ExchangeTimestamps.completion)
    listener should not be 'inMarket
  }

  it should "notify successful termination" in new Fixture {
    order.fundsRequested(orderMatch, requiredFunds)
    order.startExchange(orderMatch.exchangeId, ExchangeTimestamps.creation)
    order.completeExchange(complete(order.view.exchanges.values.head))
    listener.lastStatus shouldBe OrderStatus.Completed
  }

  it should "notify termination upon cancellation" in new Fixture {
    order.cancel(ExchangeTimestamps.completion)
    listener.lastStatus shouldBe OrderStatus.Cancelled
  }

  it should "accept order matches during other exchange" in new Fixture {
    order.fundsRequested(firstHalfMatch, halfRequiredFunds)
    order.startExchange(firstHalfMatch.exchangeId, ExchangeTimestamps.creation)
    order.fundsRequested(secondHalfMatch, halfRequiredFunds)
    order.startExchange(secondHalfMatch.exchangeId, ExchangeTimestamps.creation.plusMinutes(1))
  }

  it should "recognize already accepted matches" in new Fixture {
    order.fundsRequested(orderMatch, requiredFunds)
    val exchangeInProgress = order.startExchange(orderMatch.exchangeId, ExchangeTimestamps.creation)
    order.shouldAcceptOrderMatch(orderMatch) shouldBe MatchAlreadyAccepted(exchangeInProgress)
  }

  it should "reject order matches when order is finished" in new Fixture {
    order.cancel(ExchangeTimestamps.completion)
    order.shouldAcceptOrderMatch(orderMatch) shouldBe MatchRejected("Order already finished")
  }

  it should "support partial matching" in new Fixture {
    order.view.amounts.pending shouldBe initialOrder.amount
    order.becomeInMarket()

    inside(order.shouldAcceptOrderMatch(firstHalfMatch)) { case MatchAccepted(_) => }
    order.fundsRequested(firstHalfMatch, halfRequiredFunds)
    order.startExchange(firstHalfMatch.exchangeId, ExchangeTimestamps.creation)
    order.completeExchange(complete(order.view.exchanges.values.last))
    listener.lastOrder should not be 'inMarket

    order.view.amounts.pending shouldBe (initialOrder.amount / 2)
    order.becomeInMarket()

    inside(order.shouldAcceptOrderMatch(secondHalfMatch)) { case MatchAccepted(_) => }
    order.fundsRequested(secondHalfMatch, halfRequiredFunds)
    order.startExchange(secondHalfMatch.exchangeId, ExchangeTimestamps.creation.plusHours(1))
    order.completeExchange(complete(order.view.exchanges.values.last))
    listener.lastStatus shouldBe OrderStatus.Completed
    listener should not be 'inMarket
  }

  trait Fixture {
    protected val amountsCalculator = new DefaultAmountsCalculator()
    protected val listener = new MockOrderControllerListener[Euro.type]
    protected val order = new OrderController[Euro.type](
      peerIds.buyer, amountsCalculator, CoinffeineUnitTestNetwork, initialOrder)
    order.addListener(listener)

    protected def complete(exchange: Exchange[Euro.type]): SuccessfulExchange[Euro.type] =
      exchange match {
        case runningExchange: RunningExchange[Euro.type] =>
          runningExchange.complete(runningExchange.log.mostRecent.get.timestamp.plusMinutes(10))
        case notStarted: HandshakingExchange[Euro.type] =>
          val createdOn = notStarted.metadata.createdOn
          notStarted.handshake(participants.buyer, participants.seller, createdOn.plusSeconds(5))
            .startExchanging(FakeExchangeProtocol.DummyDeposits, createdOn.plusMinutes(10))
            .complete(createdOn.plusMinutes(20))
      }
  }
}
