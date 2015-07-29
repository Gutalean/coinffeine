package coinffeine.peer.payment.okpay.blocking

import scalaz.syntax.validation._

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId

class BlockedFiatRegistryTest extends UnitTest {

  "The blocked fiat registry" must "retrieve no blocked funds when no funds are blocked" in
    new Fixture {
      currentTotalBlockedFunds(Euro) shouldBe Euro.zero
    }

  it must "retrieve blocked funds when blocked" in new Fixture {
    givenAmounts(100.EUR)
    expectAvailableFunds(100.EUR)
    currentTotalBlockedFunds(Euro) shouldBe 100.EUR
  }

  it must "retrieve no blocked funds after unblocked" in new Fixture {
    givenAmounts(100.EUR)
    val funds = expectAvailableFunds(100.EUR)
    registry.unblock(funds)
    currentTotalBlockedFunds(Euro) shouldBe Euro.zero
  }

  it must "retrieve blocked funds after using some" in new Fixture {
    givenAmounts(100.EUR)
    val funds = expectAvailableFunds(100.EUR)
    givenFundsMarkedUsed(funds, 60.EUR)
    currentTotalBlockedFunds(Euro) shouldBe 40.EUR
  }

  it must "block funds up to existing balances" in new Fixture {
    givenAmounts(100.EUR)
    expectAvailableFunds(50.EUR)
    expectAvailableFunds(50.EUR)
    expectUnavailableFunds(50.EUR)
  }

  it must "consider remaining usage limits when blocking funds" in new Fixture {
    givenAmounts(amount = 100.EUR, remainingLimit = 50.EUR)
    expectAvailableFunds(50.EUR)
    expectUnavailableFunds(50.EUR)
  }

  it must "reject blocking funds twice for the same exchange" in new Fixture {
    givenAmounts(100.EUR)
    val funds = expectAvailableFunds(50.EUR)
    an [IllegalArgumentException] shouldBe thrownBy {
      registry.block(funds, 20.EUR)
    }
  }

  it must "unblock blocked funds to make then available again" in new Fixture {
    givenAmounts(100.EUR)
    val funds1 = expectAvailableFunds(100.EUR)
    registry.unblock(funds1)
    val funds2 = givenBlockedFunds(100.EUR)
    expectBecomingAvailable(funds2)
  }

  it must "notify unavailable funds once block submission when insufficient funds" in
    new Fixture {
      givenAmounts(100.EUR)
      val funds = givenBlockedFunds(110.EUR)
      expectBecomingUnavailable(funds)
    }

  it must "notify unavailable funds to the last ones when blocks exceeds the funds" in
    new Fixture {
      givenAmounts(100.EUR)
      val funds1 = expectAvailableFunds(90.EUR)
      val funds2 = expectAvailableFunds(10.EUR)

      givenAmounts(90.EUR)
      expectBecomingUnavailable(funds2)

      givenAmounts(50.EUR)
      expectBecomingUnavailable(funds1)
    }

  it must "notify available funds when balance is enough again due to external increase" in
    new Fixture {
      givenAmounts(100.EUR)
      val funds = expectAvailableFunds(100.EUR)
      givenAmounts(50.EUR)
      expectBecomingUnavailable(funds)
      givenAmounts(100.EUR)
      expectBecomingAvailable(funds)
    }

  it must "notify available funds when balance is enough again due to unblocking" in
    new Fixture {
      givenAmounts(100.EUR)
      val funds1 = expectAvailableFunds(50.EUR)
      val funds2 = expectAvailableFunds(50.EUR)
      givenAmounts(60.EUR)
      expectBecomingUnavailable(funds2)
      registry.unblock(funds1)
      expectBecomingAvailable(funds2)
    }

  it must "reject funds usage for unknown funds id" in new Fixture {
    val unknownFunds = ExchangeId("unknown")
    registry.canMarkUsed(unknownFunds, 10.EUR) shouldBe
        s"no funds with id $unknownFunds".failure
  }

  it must "reject funds usage when it exceeds blocked amount" in new Fixture {
    givenAmounts(100.EUR)
    val funds = expectAvailableFunds(50.EUR)
    registry.canMarkUsed(funds, 100.EUR) shouldBe
        s"insufficient blocked funds for id $funds: 100.00EUR requested, 50.00EUR available".failure
  }

  it must "reject funds usage when not available" in new Fixture {
    givenAmounts(100.EUR)
    val funds = expectUnavailableFunds(150.EUR)
    registry.canMarkUsed(funds, 100.EUR) shouldBe
        s"funds with id $funds are not currently available".failure
  }

  it must "accept funds usage when amount is less than blocked funds" in new Fixture {
    givenAmounts(100.EUR)
    val funds = expectAvailableFunds(50.EUR)
    expectFundsCanBeUsed(funds, 10.EUR)
  }

  it must "accept funds usage when amount is equals to blocked funds" in new Fixture {
    givenAmounts(100.EUR)
    val funds = expectAvailableFunds(50.EUR)
    expectFundsCanBeUsed(funds, 50.EUR)
  }

  it must "consider new balance after funds are used" in new Fixture {
    givenAmounts(100.EUR)
    val funds1 = expectAvailableFunds(50.EUR)
    givenFundsMarkedUsed(funds1, 10.EUR)

    val funds2 = givenBlockedFunds(60.EUR)
    expectBecomingUnavailable(funds2)
  }

  it must "unmark funds used" in new Fixture {
    givenAmounts(100.EUR)
    val funds = expectAvailableFunds(50.EUR)

    // Mark and unmark funds
    givenFundsMarkedUsed(funds, 50.EUR)
    givenFundsUnmarkedUsed(funds, 50.EUR)

    // Mark again
    expectFundsCanBeUsed(funds, 50.EUR)
  }

  val funds1, funds2 = ExchangeId.random()

  it must "persist blocked funds in mementos" in new Fixture {
    givenAmounts(100.EUR)
    val funds1 = givenBlockedFunds(60.EUR)
    val funds2 = givenBlockedFunds(40.EUR)
    givenFundsMarkedUsed(funds1, 20.EUR)
    registry.unblock(funds2)

    val otherRegistry = new BlockedFiatRegistry
    otherRegistry.restoreMemento(registry.takeMemento)
    otherRegistry.blockedFundsByCurrency shouldBe FiatAmounts.fromAmounts(40.EUR)
  }

  private abstract class Fixture() {
    private val listener = new MockAvailabilityListener
    protected var registry = new BlockedFiatRegistry

    protected def givenAmounts(amount: FiatAmount, remainingLimit: FiatAmount): Unit = {
      givenAmounts(amount, Some(remainingLimit))
    }

    protected def givenAmounts(
        amount: FiatAmount, remainingLimit: Option[FiatAmount] = None): Unit = {
      registry.updateTransientAmounts(
        newBalances = FiatAmounts.fromAmounts(amount),
        newRemainingLimits = FiatAmounts(remainingLimit.toSeq)
      )
    }

    protected def givenBlockedFunds(amount: FiatAmount): ExchangeId = {
      val fundsId = ExchangeId.random()
      registry.block(fundsId, amount)
      fundsId
    }

    protected def givenFundsMarkedUsed(funds: ExchangeId, amount: FiatAmount): Unit = {
      expectFundsCanBeUsed(funds, amount)
      registry.markUsed(funds, amount)
    }

    protected def givenFundsUnmarkedUsed(funds: ExchangeId, amount: FiatAmount): Unit = {
      registry.canUnmarkUsed(funds, amount) shouldBe 'success
      registry.unmarkUsed(funds, amount)
    }

    protected def expectFundsCanBeUsed(funds: ExchangeId, amount: FiatAmount): Unit = {
      registry.canMarkUsed(funds, amount) shouldBe 'success
    }


    protected def expectAvailableFunds(amount: FiatAmount): ExchangeId = {
      val fundsId = givenBlockedFunds(amount)
      expectBecomingAvailable(fundsId)
      fundsId
    }

    protected def expectUnavailableFunds(amount: FiatAmount): ExchangeId = {
      val fundsId = givenBlockedFunds(amount)
      expectBecomingUnavailable(fundsId)
      fundsId
    }

    protected def expectBecomingAvailable(funds: ExchangeId): Unit = {
      registry.notifyAvailabilityChanges(listener)
      listener.expectAvailable(funds)
    }

    protected def expectBecomingUnavailable(funds: ExchangeId): Unit = {
      registry.notifyAvailabilityChanges(listener)
      listener.expectUnavailable(funds)
    }

    protected def currentTotalBlockedFunds(currency: FiatCurrency): FiatAmount = {
      registry.blockedFundsByCurrency.get(currency).getOrElse(currency.zero)
    }
  }
}
