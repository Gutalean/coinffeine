package coinffeine.peer.market.orders.controller

import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.scalatest.mock.MockitoSugar

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.BlockedCoinsId
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange._
import coinffeine.model.market.{Bid, Order, Price}
import coinffeine.model.network.PeerId
import coinffeine.model.payment.OkPayPaymentProcessor
import coinffeine.model.payment.PaymentProcessor.BlockedFundsId
import coinffeine.peer.amounts.DefaultAmountsComponent
import coinffeine.peer.exchange.protocol.MockExchangeProtocol.DummyDeposits
import coinffeine.protocol.messages.brokerage.OrderMatch

class WaitingForMatchesStateTest extends UnitTest
  with MockitoSugar with SampleExchange with DefaultAmountsComponent {

  val notStartedOrder = Order(Bid, 100.BTC, Price(1.EUR))
  val partiallyCompletedOrder = notStartedOrder
    .withExchange(buyerHandshakingExchange.startExchanging(DummyDeposits).complete)

  "An order waiting for matches" should "reject order matches with prices off the limit" in
    new FreshInstance(notStartedOrder) {
      state.acceptOrderMatch(context, orderMatch(100.BTC, 2.EUR)) shouldBe
        MatchRejected("Invalid price")
    }

  it should "reject order matches with amounts greater than pending" in
    new FreshInstance(partiallyCompletedOrder) {
      state.acceptOrderMatch(context, orderMatch(95.BTC, 1.EUR)) shouldBe
        MatchRejected("Invalid amount")
    }

  it should "accept perfect matches" in new FreshInstance(notStartedOrder) {
    givenAvailableFunds()
    val MatchAccepted(exchange) = state.acceptOrderMatch(context, orderMatch(100.BTC, 1.EUR))
    exchange.amounts.netBitcoinExchanged shouldBe 100.BTC
    verify(context).keepOffMarket()
  }

  it should "accept partial matches" in new FreshInstance(partiallyCompletedOrder) {
    givenAvailableFunds()
    val MatchAccepted(exchange) = state.acceptOrderMatch(context, orderMatch(50.BTC, 1.EUR))
    exchange.amounts.netBitcoinExchanged shouldBe 50.BTC
    verify(context).keepOffMarket()
  }

  abstract class FreshInstance(val order: Order[Euro.type]) {
    val state = new WaitingForMatchesState[Euro.type]
    val context = mock[StateContext[Euro.type]]
    given(context.order).willReturn(order)
    given(context.calculator).willReturn(amountsCalculator)
    val funds = new FakeOrderFunds
    given(context.funds).willReturn(funds)

    def orderMatch(amount: BitcoinAmount, price: Euro.Amount) = {
      val fiatSpent = price * amount.value
      OrderMatch(
        order.id, ExchangeId.random(),
        Both(buyer = amount, seller = amount + 0.0003.BTC),
        Both(buyer = fiatSpent, seller = OkPayPaymentProcessor.amountMinusFee(fiatSpent)),
        20, PeerId("counterpart")
      )
    }

    def givenAvailableFunds(): Unit = {
      funds.blockFunds(Exchange.BlockedFunds(Some(BlockedFundsId(1)), BlockedCoinsId(2)))
      funds.makeAvailable()
    }
  }
}
