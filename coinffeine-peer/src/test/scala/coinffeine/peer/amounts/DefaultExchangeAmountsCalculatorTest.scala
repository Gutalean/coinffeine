package coinffeine.peer.amounts

import java.math.BigInteger

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.BitcoinFeeCalculator
import coinffeine.model.currency.Currency.{Bitcoin, Euro}
import coinffeine.model.currency.Implicits._
import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange.Amounts
import coinffeine.model.payment.PaymentProcessor

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
      amounts.deposits.buyer should be (amounts.steps.head.bitcoinAmount * 2)
    }
  }

  it must "require the seller to deposit one steps worth of bitcoins apart from the principal" in
    new Fixture {
      forAnyAmountOrPrice { amounts =>
        amounts.deposits.seller - amounts.bitcoinExchanged should be (amounts.steps.head.bitcoinAmount)
      }
    }

  it must "refund deposited amounts but one step worth of bitcoins" in new Fixture {
    forAnyAmountOrPrice { amounts =>
      amounts.deposits.buyer - amounts.refunds.buyer should be (amounts.steps.head.bitcoinAmount)
      amounts.deposits.seller - amounts.refunds.seller should be (amounts.steps.head.bitcoinAmount)
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
      stepsSum.bitcoinAmount should be (bitcoinAmount)
      stepsSum.fiatAmount should be (price * bitcoinAmount.value)
    }
  }

  it must "add fiat fees to each step and to the required fiat amount" in
    new Fixture(paymentProcessor = new FixedFeeProcessor(0.5)) {
      forAnyAmountOrPrice { (bitcoinAmount, price, amounts) =>
        amounts.steps.map(_.fiatFee).toSet should be (Set(0.5.EUR))
        amounts.fiatRequired.buyer - amounts.fiatExchanged should be (5.EUR)
      }
    }

  it must "split 3 transaction fees equally between buyer and seller" in
    new Fixture(bitcoinFeeCalculator = new FixedBitcoinFee(0.001.BTC)) {
      forAnyAmountOrPrice { amounts =>
        amounts.transactionFee should be (0.001.BTC)
      }
    }

  val exampleCases = Seq(
    1.BTC -> 500.EUR,
    2.BTC -> 3000.EUR,
    0.3.BTC -> 1234.EUR
  )

  abstract class Fixture(paymentProcessor: PaymentProcessor = NoFeesProcessor,
                         bitcoinFeeCalculator: BitcoinFeeCalculator = NoBitcoinFees) {

    val instance = new DefaultExchangeAmountsCalculator(paymentProcessor, bitcoinFeeCalculator)

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

  object NoFeesProcessor extends PaymentProcessor {
    override def calculateFee[C <: FiatCurrency](amount: CurrencyAmount[C]) =
      CurrencyAmount.zero(amount.currency)
  }

  class FixedFeeProcessor(fee: BigDecimal) extends PaymentProcessor {
    override def calculateFee[C <: FiatCurrency](amount: CurrencyAmount[C]) =
      CurrencyAmount(fee, amount.currency)
  }

  object NoBitcoinFees extends BitcoinFeeCalculator {
    override val defaultTransactionFee: BitcoinAmount = 0.BTC
  }

  class FixedBitcoinFee(fee: BitcoinAmount) extends BitcoinFeeCalculator {
    override val defaultTransactionFee: BitcoinAmount = fee
  }
}
