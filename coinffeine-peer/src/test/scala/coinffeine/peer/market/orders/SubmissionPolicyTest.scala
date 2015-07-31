package coinffeine.peer.market.orders

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.currency.balance.{FiatBalance, BitcoinBalance}
import coinffeine.model.market.OrderBookEntry
import coinffeine.model.order.{Ask, LimitPrice, Bid, MarketPrice}
import coinffeine.model.util.{CacheStatus, Cached}
import coinffeine.peer.amounts.DefaultAmountsCalculator

class SubmissionPolicyTest extends UnitTest {

  "The submission policy" should "submit nothing if no entry is present" in new Fixture {
    policy.unsetEntry()
    policy.entryToSubmit shouldBe 'empty
  }

  it should "submit any available entry if there is enough fiat and bitcoin" in new Fixture {
    policy.setEntry(limitedBid)
    givenEnoughBitcoinBalance()
    givenEnoughFiatBalance()
    policy.entryToSubmit shouldBe Some(limitedBid)
  }

  it should "submit nothing if there is enough bitcoin" in new Fixture {
    policy.setEntry(limitedBid)
    givenNotEnoughBitcoinBalance()
    givenEnoughFiatBalance()
    policy.entryToSubmit shouldBe 'empty
  }

  it should "submit nothing if there are bitcoins but they are blocked" in new Fixture {
    policy.setEntry(limitedBid)
    givenBlockedBitcoinBalance()
    givenEnoughFiatBalance()
    policy.entryToSubmit shouldBe 'empty
  }

  it should "submit nothing if there are bitcoins but they are unavailable" in new Fixture {
    policy.setEntry(limitedBid)
    givenUnavailableBitcoinBalance()
    givenEnoughFiatBalance()
    policy.entryToSubmit shouldBe 'empty
  }

  it should "submit nothing if fiat balance is not fresh" in new Fixture {
    policy.setEntry(limitedBid)
    givenEnoughBitcoinBalance()
    givenStaleFiatBalance()
    policy.entryToSubmit shouldBe 'empty
  }

  it should "submit nothing if fiat balance is not enough" in new Fixture {
    policy.setEntry(limitedBid)
    givenEnoughBitcoinBalance()
    givenNotEnoughFiatBalance()
    policy.entryToSubmit shouldBe 'empty
  }

  it should "submit nothing if fiat balance is blocked" in new Fixture {
    policy.setEntry(limitedBid)
    givenEnoughBitcoinBalance()
    givenBlockedFiatBalance()
    policy.entryToSubmit shouldBe 'empty
  }

  it should "submit nothing if transference limit will be exceeded" in new Fixture {
    policy.setEntry(limitedBid)
    givenEnoughBitcoinBalance()
    givenInsufficientRemainingLimit()
    policy.entryToSubmit shouldBe 'empty
  }

  it should "submit market price asks if there is enough bitcoin" in new Fixture {
    policy.setEntry(marketPriceAsk)
    givenEnoughBitcoinBalance()
    givenNoFiatBalance()
    policy.entryToSubmit shouldBe Some(marketPriceAsk)
  }

  it should "submit no market price ask if there is not enough bitcoin" in new Fixture {
    policy.setEntry(marketPriceAsk)
    givenNotEnoughBitcoinBalance()
    givenNoFiatBalance()
    policy.entryToSubmit shouldBe 'empty
  }

  it should "submit market price bids if the minimum payment and deposit are available" in
    new Fixture {
      policy.setEntry(marketPriceBid)
      givenEnoughBitcoinBalance()
      givenEnoughFiatBalance()
      policy.entryToSubmit shouldBe Some(marketPriceBid)
    }

  it should "submit no market price bid if there is less than the minimum fiat payment" in
    new Fixture {
      policy.setEntry(marketPriceBid)
      givenEnoughBitcoinBalance()
      givenNoFiatBalance()
      policy.entryToSubmit shouldBe 'empty
    }

  it should "submit no market price bid if there is less than the minimum deposit available" in
    new Fixture {
      policy.setEntry(marketPriceBid)
      givenNotEnoughBitcoinBalance()
      givenEnoughFiatBalance()
      policy.entryToSubmit shouldBe 'empty
    }

  trait Fixture {
    protected val marketPriceBid = OrderBookEntry.random(Bid, 1.BTC, MarketPrice(Euro))
    protected val marketPriceAsk = OrderBookEntry.random(Ask, 1.BTC, MarketPrice(Euro))
    protected val limitedBid = OrderBookEntry.random(Bid, 1.BTC, LimitPrice(10.EUR))

    private val calculator = new DefaultAmountsCalculator()
    protected val policy = new SubmissionPolicyImpl(calculator)

    protected def givenEnoughFiatBalance(): Unit = {
      givenFiatBalance(100.EUR)
    }

    protected def givenNoFiatBalance(): Unit = {
      givenFiatBalance(0.EUR)
    }

    protected def givenNotEnoughFiatBalance(): Unit = {
      givenFiatBalance(1.EUR)
    }

    protected def givenStaleFiatBalance(): Unit = {
      givenFiatBalance(100.EUR, cacheStatus = CacheStatus.Stale)
    }

    protected def givenBlockedFiatBalance(): Unit = {
      givenFiatBalance(100.EUR, blocked = Some(100.EUR))
    }

    protected def givenInsufficientRemainingLimit(): Unit = {
      givenFiatBalance(100.EUR, remainingLimit = Some(1.EUR))
    }

    private def givenFiatBalance(
        amount: FiatAmount,
        blocked: Option[FiatAmount] = None,
        remainingLimit: Option[FiatAmount] = None,
        cacheStatus: CacheStatus = CacheStatus.Fresh): Unit = {
      val balance = FiatBalance(
        amounts = FiatAmounts.fromAmounts(amount),
        blockedAmounts = FiatAmounts(blocked.toSeq),
        remainingLimits = FiatAmounts(remainingLimit.toSeq)
      )
      policy.setFiatBalance(Cached(balance, cacheStatus))
    }

    protected def givenEnoughBitcoinBalance(): Unit = {
      policy.setBitcoinBalance(BitcoinBalance.singleOutput(1.BTC))
    }

    protected def givenNotEnoughBitcoinBalance(): Unit = {
      policy.setBitcoinBalance(BitcoinBalance.singleOutput(Bitcoin.satoshi))
    }

    protected def givenBlockedBitcoinBalance(): Unit = {
      policy.setBitcoinBalance(BitcoinBalance.singleOutput(1.BTC).copy(blocked = 1.BTC))
    }

    protected def givenUnavailableBitcoinBalance(): Unit = {
      policy.setBitcoinBalance(BitcoinBalance.singleOutput(1.BTC).copy(available = 0.BTC))
    }
  }
}
