package coinffeine.peer.market.orders.controller

import org.joda.time.DateTime
import org.scalatest.Inside

import coinffeine.common.test.UnitTest
import coinffeine.model.Both
import coinffeine.model.bitcoin.KeyPair
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.currency._
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.peer.amounts.DefaultAmountsComponent
import coinffeine.protocol.messages.brokerage.OrderMatch

class OrderMatchValidatorTest extends UnitTest with Inside with DefaultAmountsComponent {

  private val ownId = PeerId.random()
  private val validator = new OrderMatchValidator(ownId, amountsCalculator)
  private val limitOrder = Order.randomLimit(Bid, 0.9997.BTC, Price(110.263078923677103, Euro))
  private val marketPriceOrder = limitOrder.copy(price = MarketPrice(Euro))
  private val orderMatch = OrderMatch(
    orderId = limitOrder.id,
    exchangeId = ExchangeId.random(),
    bitcoinAmount = Both(0.9997.BTC, 1.BTC),
    fiatAmount = Both(100.EUR, 99.5.EUR),
    lockTime = 37376,
    counterpart = PeerId.random()
  )
  private val exchange = Exchange.create[Euro.type](
    id = orderMatch.exchangeId,
    role = BuyerRole,
    counterpartId = orderMatch.counterpart,
    amounts = amountsCalculator.exchangeAmountsFor(orderMatch),
    parameters = Exchange.Parameters(orderMatch.lockTime, network = CoinffeineUnitTestNetwork),
    createdOn = DateTime.now()
  )
  private val handshakeStartedOn = exchange.metadata.createdOn.plusSeconds(5)

  "An order match validator" should "reject any order match if the order is finished" in {
    expectRejectionWithMessage(limitOrder.cancel, orderMatch, "Order already finished")
  }

  it should "reject already accepted matches" in {
    val exchangingOrder = limitOrder.withExchange(exchange)
    inside(validator.shouldAcceptOrderMatch(
      exchangingOrder, orderMatch, orderMatch.bitcoinAmount.buyer)) {
        case MatchAlreadyAccepted(_) =>
      }
  }

  it should "reject self-matches" in {
    expectRejectionWithMessage(limitOrder, orderMatch.copy(counterpart = ownId), "Self-cross")
  }

  it should "reject matches of more amount than the pending one" in {
    val tooLargeOrderMatch = orderMatch.copy[Euro.type](bitcoinAmount = Both(1.9997.BTC, 2.BTC))
    expectRejectionWithMessage(limitOrder, tooLargeOrderMatch, "Invalid amount")
  }

  it should "reject matches whose price is off limit when buying" in {
    val tooHighPriceMatch = orderMatch.copy(fiatAmount = Both(120.EUR, 119.5.EUR))
    expectRejectionWithMessage(limitOrder, tooHighPriceMatch, "Invalid price")
  }

  it should "reject matches whose price is off limit when selling" in {
    val tooLowPriceMatch = orderMatch.copy(fiatAmount = Both(80.EUR, 79.5.EUR))
    val sellingOrder = limitOrder.copy(
      orderType = Ask,
      amount = 1.BTC,
      price = LimitPrice(Price(109.68.EUR))
    )
    expectRejectionWithMessage(sellingOrder, tooLowPriceMatch, "Invalid price")
  }

  it should "reject matches with inconsistent amounts" in {
    val inconsistentOrderMatch = orderMatch.copy(
      bitcoinAmount = Both(0.5.BTC, 0.5.BTC),
      fiatAmount = orderMatch.fiatAmount.map(_ / 2)
    )
    expectRejectionWithMessage(limitOrder, inconsistentOrderMatch, "Match with inconsistent amounts")
  }

  it should "accept valid limit order matches" in {
    inside(validator.shouldAcceptOrderMatch(limitOrder, orderMatch, alreadyBlocking = 0.BTC)) {
      case MatchAccepted(_) =>
    }
  }

  it should "accept valid market price order matches" in {
    inside(validator.shouldAcceptOrderMatch(marketPriceOrder, orderMatch, alreadyBlocking = 0.BTC)) {
      case MatchAccepted(_) =>
    }
  }

  it should "take rounding into account when checking the price limit" in {
    val boundaryOrderMatch = orderMatch.copy(fiatAmount = Both(110.23.EUR,109.68.EUR))
    inside(validator.shouldAcceptOrderMatch(limitOrder, boundaryOrderMatch, alreadyBlocking = 0.BTC)) {
      case MatchAccepted(_) =>
    }
  }

  it should "take pending funds requests into account" in {
    expectRejectionWithMessage(limitOrder, orderMatch, "Invalid amount", alreadyBlocking = 0.5.BTC)
  }

  it should "accept matches when having a running exchange" in {
    val runningExchange = exchange.copy[Euro.type](
      metadata = exchange.metadata.copy(id = ExchangeId.random())
    ).handshake(
      user = Exchange.PeerInfo("account1", new KeyPair),
      counterpart = Exchange.PeerInfo("account2", new KeyPair),
      timestamp = handshakeStartedOn
    )
    val exchangingOrder = limitOrder.copy(amount = 10.BTC).withExchange(runningExchange)
    inside(validator.shouldAcceptOrderMatch(exchangingOrder, orderMatch, alreadyBlocking = 0.BTC)) {
      case MatchAccepted(_) =>
    }
  }

  private def expectRejectionWithMessage(order: Order[Euro.type],
                                         orderMatch: OrderMatch[Euro.type],
                                         message: String,
                                         alreadyBlocking: Bitcoin.Amount = 0.BTC): Unit = {
    inside(validator.shouldAcceptOrderMatch(order, orderMatch, alreadyBlocking)) {
      case MatchRejected(completeMessage) =>
        completeMessage should include(message)
    }
  }
}
