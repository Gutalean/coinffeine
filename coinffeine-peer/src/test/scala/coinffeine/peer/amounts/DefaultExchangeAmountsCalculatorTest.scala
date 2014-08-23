package coinffeine.peer.amounts

import java.math.BigInteger

import coinffeine.common.test.UnitTest
import coinffeine.model.currency.Currency.{Bitcoin, Euro}
import coinffeine.model.currency.Implicits._
import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange.{Amounts, StepAmounts}

class DefaultExchangeAmountsCalculatorTest extends UnitTest {

  "The funds calculator" must "reject non positive bitcoin amounts" in new Fixture {
    an [IllegalArgumentException] shouldBe thrownBy {
      instance.amountsFor(bitcoinAmount = 0.BTC, price = 1.EUR)
    }
  }

  it must "reject non positive prices" in new Fixture {
    an [IllegalArgumentException] shouldBe thrownBy {
      instance.amountsFor(bitcoinAmount = 1.BTC, price = 0.EUR)
    }
  }

  it must "reject amounts not divisible by the number of steps" in new Fixture {
    an [IllegalArgumentException] shouldBe thrownBy {
      instance.amountsFor(1.BTC, 1.01.EUR)
    }
    an [IllegalArgumentException] shouldBe thrownBy {
      instance.amountsFor(Bitcoin.fromSatoshi(BigInteger.ONE), 1.EUR)
    }
  }

  it must "have ten steps independently of amount or price" in new Fixture {
    forAnyAmountOrPrice { amounts =>
      amounts.breakdown.intermediateSteps should be (10)
    }
  }

  it must "require the buyer to deposit two steps worth of bitcoins" in new Fixture {
    forAnyAmountOrPrice { amounts =>
      amounts.deposits.buyer should be (amounts.stepAmounts.bitcoinAmount * 2)
    }
  }

  it must "require the seller to deposit one steps worth of bitcoins apart from the principal" in
    new Fixture {
      forAnyAmountOrPrice { amounts =>
        amounts.deposits.seller - amounts.bitcoinExchanged should be (amounts.stepAmounts.bitcoinAmount)
      }
    }

  it must "refund deposited amounts but one step worth of bitcoins" in new Fixture {
    forAnyAmountOrPrice { amounts =>
      amounts.deposits.buyer - amounts.refunds.buyer should be (amounts.stepAmounts.bitcoinAmount)
      amounts.deposits.seller - amounts.refunds.seller should be (amounts.stepAmounts.bitcoinAmount)
    }
  }

  it must "have same sized steps" in new Fixture {
    forAnyAmountOrPrice { amounts =>
      amounts.steps.toSet should have size 1
    }
  }

  it must "have steps summing up to the total amounts" in new Fixture {
    forAnyAmountOrPrice { (bitcoinAmount, price, amounts) =>
      val stepsSum = amounts.steps.reduce(_ + _)
      stepsSum should be (StepAmounts(bitcoinAmount, price * bitcoinAmount.value))
    }
  }

  val exampleCases = Seq(
    1.BTC -> 500.EUR,
    2.BTC -> 3000.EUR,
    0.3.BTC -> 1234.EUR
  )

  trait Fixture {

    val instance = new DefaultExchangeAmountsCalculator

    type Euros = Euro.type

    def forAnyAmountOrPrice(test: Amounts[Euros] => Unit): Unit = {
      forAnyAmountOrPrice((_, _, amounts) => test(amounts))
    }

    def forAnyAmountOrPrice(test: (BitcoinAmount, CurrencyAmount[Euros], Amounts[Euros]) => Unit): Unit = {
      for ((bitcoin, price) <- exampleCases) {
        test(bitcoin, price, instance.amountsFor(bitcoin, price))
      }
    }
  }
}
