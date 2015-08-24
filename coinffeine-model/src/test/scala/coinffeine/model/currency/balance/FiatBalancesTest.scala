package coinffeine.model.currency.balance

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._

class FiatBalancesTest extends UnitTest {

  "A multiple-currency fiat balance" should "be diced to a concrete currency" in {
    val balances = FiatBalances(
      amounts = FiatAmounts.fromAmounts(10.EUR, 5.USD),
      blockedAmounts = FiatAmounts.fromAmounts(0.EUR),
      remainingLimits = FiatAmounts.fromAmounts(100.EUR)
    )
    balances.balanceFor(Euro) shouldBe FiatBalance(
      amount = 10.EUR,
      blockedAmount = 0.EUR,
      remainingLimit = Some(100.EUR)
    )
    balances.balanceFor(UsDollar) shouldBe FiatBalance(
      amount = 5.USD,
      blockedAmount = 0.USD,
      remainingLimit = None
    )
  }

  "A single-currency fiat balance" should "require a single currency" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      FiatBalance(10.EUR, 4.USD, remainingLimit = None)
    }
    an [IllegalArgumentException] shouldBe thrownBy {
      FiatBalance(10.EUR, 4.EUR, remainingLimit = Some(10.USD))
    }
  }
}
