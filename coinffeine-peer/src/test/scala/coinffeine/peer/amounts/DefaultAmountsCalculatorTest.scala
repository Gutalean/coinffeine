package coinffeine.peer.amounts

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.BitcoinFeeCalculator
import coinffeine.model.currency.Currency.{Bitcoin, Euro}
import coinffeine.model.currency.Implicits._
import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange.Amounts
import coinffeine.model.payment.PaymentProcessor

class DefaultAmountsCalculatorTest extends UnitTest {

  "The funds calculator" must "reject non positive bitcoin amounts" in new Fixture {
    an [IllegalArgumentException] shouldBe thrownBy {
      instance.exchangeAmountsFor(bitcoinAmount = 0.BTC, fiatAmount = 1.EUR)
    }
  }

  it must "reject non positive prices" in new Fixture {
    an [IllegalArgumentException] shouldBe thrownBy {
      instance.exchangeAmountsFor(bitcoinAmount = 1.BTC, fiatAmount = 0.EUR)
    }
  }

  it must "have all steps but the last of the optimum size for the payment processor" in
    new Fixture {
      forAnyAmounts { amounts =>
        amounts.steps.init.forall(_.fiatAmount == paymentProcessor.bestStepSize(Euro))
      }
    }

  it must "require the buyer to deposit two steps worth of bitcoins" in new Fixture {
    forAnyAmounts { amounts =>
      val depositStep = amounts.steps.map(_.bitcoinAmount).reduce(_ max _)
      amounts.deposits.buyer should be (depositStep * 2)
    }
  }

  it must "require the seller to deposit one steps worth of bitcoins apart from the principal" in
    new Fixture {
      forAnyAmounts { amounts =>
        val depositStep = amounts.steps.map(_.bitcoinAmount).reduce(_ max _)
        amounts.deposits.seller - amounts.bitcoinExchanged should be (depositStep)
      }
    }

  it must "refund deposited amounts but one step worth of bitcoins" in new Fixture {
    forAnyAmounts { amounts =>
      val depositStep = amounts.steps.map(_.bitcoinAmount).reduce(_ max _)
      amounts.deposits.buyer - amounts.refunds.buyer should be (depositStep)
      amounts.deposits.seller - amounts.refunds.seller should be (depositStep)
    }
  }

  it must "have all but last steps of the same fiat size" in new Fixture {
    forAnyAmounts { amounts =>
      amounts.steps.init.map(_.fiatAmount).toSet should have size 1
    }
  }

  it must "have all but last steps of about same bitcoin size" in new Fixture {
    forAnyAmounts { amounts =>
      val steps = amounts.steps.init.map(_.bitcoinAmount.value)
      steps.max should equal (steps.min +- Bitcoin.fromSatoshi(1).value)
    }
  }

  it must "have steps summing up to the total amounts" in new Fixture {
    forAnyAmounts { (bitcoinAmount, fiatAmount, amounts) =>
      val stepsSum = amounts.steps.reduce(_ + _)
      stepsSum.bitcoinAmount should be (bitcoinAmount)
      stepsSum.fiatAmount should be (fiatAmount)
    }
  }

  it must "add fiat fees to each step and to the required fiat amount" in
    new Fixture(paymentProcessor = new FixedFeeProcessor(0.5)) {
      forAnyAmounts { (bitcoinAmount, price, amounts) =>
        amounts.steps.map(_.fiatFee).toSet should be (Set(0.5.EUR))
        amounts.fiatRequired.buyer - amounts.fiatExchanged should be (0.5.EUR * amounts.steps.size)
      }
    }

  it must "split 3 transaction fees equally between buyer and seller" in
    new Fixture(bitcoinFeeCalculator = new FixedBitcoinFee(0.001.BTC)) {
      forAnyAmounts { amounts =>
        amounts.transactionFee should be (0.001.BTC)
      }
    }

  val exampleCases = Seq(
    1.BTC -> 500.EUR,
    2.BTC -> 6000.EUR,
    0.3.BTC -> 370.2.EUR
  )

  abstract class Fixture(val paymentProcessor: PaymentProcessor = NoFeesProcessor,
                         val bitcoinFeeCalculator: BitcoinFeeCalculator = NoBitcoinFees) {

    val instance = new DefaultAmountsCalculator(paymentProcessor, bitcoinFeeCalculator)

    type Euros = Euro.type

    def forAnyAmounts(test: Amounts[Euros] => Unit): Unit = {
      forAnyAmounts((_, _, amounts) => test(amounts))
    }

    def forAnyAmounts(test: (BitcoinAmount, CurrencyAmount[Euros], Amounts[Euros]) => Unit): Unit = {
      for ((bitcoin, amount) <- exampleCases) {
        withClue(s"exchanging $bitcoin for $amount: ") {
          test(bitcoin, amount, instance.exchangeAmountsFor(bitcoin, amount))
        }
      }
    }
  }

  object NoFeesProcessor extends PaymentProcessor {
    override def calculateFee[C <: FiatCurrency](amount: CurrencyAmount[C]) =
      CurrencyAmount.zero(amount.currency)
    override def bestStepSize[C <: FiatCurrency](currency: C) = CurrencyAmount(10, currency)
  }

  class FixedFeeProcessor(fee: BigDecimal) extends PaymentProcessor {
    override def calculateFee[C <: FiatCurrency](amount: CurrencyAmount[C]) =
      CurrencyAmount(fee, amount.currency)
    override def bestStepSize[C <: FiatCurrency](currency: C) = CurrencyAmount(10, currency)
  }

  object NoBitcoinFees extends BitcoinFeeCalculator {
    override val defaultTransactionFee: BitcoinAmount = 0.BTC
  }

  class FixedBitcoinFee(fee: BitcoinAmount) extends BitcoinFeeCalculator {
    override val defaultTransactionFee: BitcoinAmount = fee
  }
}
