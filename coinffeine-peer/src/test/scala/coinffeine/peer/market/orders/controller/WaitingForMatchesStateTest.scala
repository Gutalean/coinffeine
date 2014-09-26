package coinffeine.peer.market.orders.controller

import scala.util.{Failure, Success}

import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Matchers.any
import org.mockito.Mockito.verify
import org.scalatest.mock.MockitoSugar

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.BlockedCoinsId
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.model.payment.{OkPayPaymentProcessor, PaymentProcessor}
import coinffeine.peer.amounts.DefaultAmountsComponent
import coinffeine.peer.exchange.protocol.MockExchangeProtocol.DummyDeposits
import coinffeine.protocol.messages.brokerage.OrderMatch

class WaitingForMatchesStateTest extends UnitTest
  with MockitoSugar with SampleExchange with DefaultAmountsComponent {

  // TODO: add test cases

  val nonStartedOrder = Order(Bid, 100.BTC, Price(1.EUR))
  val partiallyCompletedOrder = nonStartedOrder
    .withExchange(buyerHandshakingExchange.startExchanging(DummyDeposits).complete)
  val blockedFunds =
    Exchange.BlockedFunds(Some(PaymentProcessor.BlockedFundsId(1)), BlockedCoinsId(2))

  "When waiting for matches" should "be initially offline and trying to get to the market" in
    new FreshInstance {
      state.enter(context)
      verify(context).updateOrderStatus(OfflineOrder)
      verify(context).keepInMarket()
    }

  it should "reject order matches with prices off the limit" in new FreshInstance {
    val m = orderMatch(100.BTC, 2.EUR)
    state.acceptOrderMatch(context, m)
    verify(context).resolveOrderMatch(m, MatchRejected("Invalid price"))
  }

  it should "reject order matches with amounts greater than pending" in
    new FreshInstance(partiallyCompletedOrder) {
      val m = orderMatch(95.BTC, 1.EUR)
      state.acceptOrderMatch(context, m)
      verify(context).resolveOrderMatch(m, MatchRejected("Invalid amount"))
    }

  it should "reject order matches when funds cannot be blocked" in new FreshInstance {
    val m = orderMatch(95.BTC, 1.EUR)
    state.acceptOrderMatch(context, m)
    state.fundsRequestResult(context, Failure(new Error("injected error")))
    verify(context).resolveOrderMatch(m, MatchRejected("Cannot block funds"))
  }

  it should "reject order matches when amounts make no sense" in new FreshInstance {
    val m = OrderMatch(
      OrderId.random(),
      ExchangeId.random(),
      Both(buyer = 100.BTC, seller = 3.BTC),
      Both(buyer = 10.EUR, seller = 20.EUR),
      lockTime = 10,
      counterpart = PeerId("counterpart")
    )
    state.acceptOrderMatch(context, m)
    verify(context).resolveOrderMatch(m, MatchRejected("Match with inconsistent amounts"))
  }

  it should "reject order matches when blocking funds for other order match" in  new FreshInstance {
    val match1 = orderMatch(95.BTC, 1.EUR)
    val match2 = orderMatch(50.BTC, 1.2.EUR)
    state.acceptOrderMatch(context, match1)
    state.acceptOrderMatch(context, match2)
    verify(context).resolveOrderMatch(match2, MatchRejected("Accepting other match"))
  }

  it should "accept perfect matches" in new FreshInstance {
    state.acceptOrderMatch(context, orderMatch(100.BTC, 1.EUR))
    state.fundsRequestResult(context, Success(blockedFunds))
    verify(context).keepOffMarket()
    val MatchAccepted(exchange) = matchResult()
    exchange.amounts.netBitcoinExchanged shouldBe 100.BTC
  }

  it should "accept partial matches" in new FreshInstance(partiallyCompletedOrder) {
    state.acceptOrderMatch(context, orderMatch(50.BTC, 1.EUR))
    state.fundsRequestResult(context, Success(blockedFunds))
    verify(context).keepOffMarket()
    val MatchAccepted(exchange) = matchResult()
    exchange.amounts.netBitcoinExchanged shouldBe 50.BTC
  }

  abstract class FreshInstance(val order: Order[Euro.type] = nonStartedOrder) {
    val state = new WaitingForMatchesState[Euro.type]
    val context = mock[StateContext[Euro.type]]
    given(context.order).willReturn(order)
    given(context.calculator).willReturn(amountsCalculator)
    val funds = new FakeFundsBlocker

    def orderMatch(amount: BitcoinAmount, price: Euro.Amount) = {
      val fiatSpent = price * amount.value
      OrderMatch(
        order.id, ExchangeId.random(),
        Both(buyer = amount, seller = amount + 0.0003.BTC),
        Both(buyer = fiatSpent, seller = OkPayPaymentProcessor.amountMinusFee(fiatSpent)),
        20, PeerId("counterpart")
      )
    }

    def matchResult(): MatchResult[Euro.type] = {
      val result = ArgumentCaptor.forClass(classOf[MatchResult[Euro.type]])
      verify(context).resolveOrderMatch(any[OrderMatch[Euro.type]], result.capture())
      result.getValue
    }
  }
}
