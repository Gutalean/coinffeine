package coinffeine.model.currency.balance

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._

class FiatBalancesTest extends UnitTest {

  "Fiat balances" should "fail to create balances from duplicated currencies" in {
    an [IllegalArgumentException] shouldBe thrownBy { FiatBalances.fromAmounts(10.EUR, 5.EUR) }
  }

  it should "create balances from non duplicated currencies" in {
    val balances = FiatBalances.fromAmounts(10.EUR, 5.USD)
    balances.get(Euro).get.amount shouldBe 10.EUR
    balances.get(UsDollar).get.amount shouldBe 5.USD
  }

  it should "have no remaining limit when unspecified" in {
    val balances = FiatBalances.fromAmounts(10.EUR, 5.USD)
    balances.get(Euro).get.remainingLimit shouldBe 'empty
    balances.get(UsDollar).get.remainingLimit shouldBe 'empty
  }

  it should "have no balance for currencies not set" in {
    val balances = FiatBalances.fromAmounts(10.EUR)
    balances.get(UsDollar) shouldBe 'empty
  }

  it should "have amount & remaining limit when both specified" in {
    val balances = FiatBalances.fromBalances(10.EUR -> 2.EUR, 5.USD -> 200.USD)
    balances.get(Euro).get shouldBe FiatBalances.Balance(10.EUR, Some(2.EUR))
    balances.get(UsDollar).get shouldBe FiatBalances.Balance(5.USD, Some(200.USD))
  }

  it should "fail when currencies are mixed in amount and remaining limit" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      FiatBalances.fromBalances(10.EUR -> 2.USD)
    }
  }

  it should "build by additions to existing balances" in {
    val balances = FiatBalances.empty
      .withBalance(10.EUR, 2.EUR)
      .withAmount(5.USD)
    balances.get(Euro).get shouldBe FiatBalances.Balance(10.EUR, Some(2.EUR))
    balances.get(UsDollar).get shouldBe FiatBalances.Balance(5.USD, remainingLimit = None)
  }

  it should "fail to build by additions when currencies are mixed in amount and limit" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      FiatBalances.empty.withBalance(10.EUR, 2.USD)
    }
  }
}
