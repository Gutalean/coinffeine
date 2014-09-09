package coinffeine.peer.amounts

import scala.math.BigDecimal.RoundingMode

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
      instance.exchangeAmountsFor(netBitcoinAmount = 0.BTC, fiatAmount = 1.EUR)
    }
  }

  it must "reject non positive prices" in new Fixture {
    an [IllegalArgumentException] shouldBe thrownBy {
      instance.exchangeAmountsFor(netBitcoinAmount = 1.BTC, fiatAmount = 0.EUR)
    }
  }

  it must "have all intermediate steps but the last of the optimum size for the payment processor" in
    new Fixture {
      forAnyAmounts { amounts =>
        amounts.intermediateSteps.init.forall(_.fiatAmount == paymentProcessor.bestStepSize(Euro))
      }
    }

  it must "have all intermediate steps splitting the bitcoin exchange amount" in new Fixture {
    forAnyAmounts { (bitcoinAmount, _, amounts) =>
      amounts.intermediateSteps.foreach { step =>
        withClue(s"at step $step") {
          step.depositSplit.toSeq.reduce(_ + _) shouldBe bitcoinAmount
        }
      }
    }
  }

  it must "have last step splitting the deposited amount" in new Fixture {
    forAnyAmounts { (bitcoinAmount, _, amounts) =>
      val splitAmount = amounts.finalStep.depositSplit.toSeq.reduce(_ + _)
      val depositAmount = amounts.depositTransactionAmounts.map(_.input).toSeq.reduce(_ + _)
      splitAmount shouldBe (depositAmount - amounts.transactionFee * 3)
    }
  }

  it must "require the buyer to deposit two steps worth of bitcoins" in new Fixture {
    private val amounts = instance.exchangeAmountsFor(1.BTC, 100.EUR)
    amounts.depositTransactionAmounts.buyer.input should be (0.2.BTC)
  }

  it must "require the seller to deposit one steps worth of bitcoins apart from the principal" in
    new Fixture {
      private val amounts = instance.exchangeAmountsFor(1.BTC, 100.EUR)
      amounts.depositTransactionAmounts.seller.input should be (1.1.BTC)
    }

  it must "refund deposited amounts but one step worth of bitcoins" in new Fixture {
    val amounts = instance.exchangeAmountsFor(1.BTC, 100.EUR)
    amounts.depositTransactionAmounts.buyer.input - amounts.refunds.buyer should be (0.1.BTC)
    amounts.depositTransactionAmounts.seller.input - amounts.refunds.seller should be (0.1.BTC)
  }

  it must "have all but last steps of the same fiat size" in new Fixture {
    forAnyAmounts { amounts =>
      amounts.intermediateSteps.init.map(_.fiatAmount).toSet should have size 1
    }
  }

  it must "have all but last steps of about same bitcoin size" in new Fixture {
    forAnyAmounts { amounts =>
      val stepIncrements = amounts.intermediateSteps.init
        .map(_.depositSplit.buyer.value)
        .sliding(2).map { case Seq(prev, next) =>
          next - prev
        }.toList
      stepIncrements.max should equal (stepIncrements.min +- Bitcoin.fromSatoshi(1).value)
    }
  }

  it must "have steps summing up to the total amounts" in new Fixture {
    forAnyAmounts { (bitcoinAmount, fiatAmount, amounts) =>
      for (step <- amounts.intermediateSteps) {
        step.depositSplit.toSeq.reduce(_ + _) should be (bitcoinAmount)
      }
      amounts.intermediateSteps.map(_.fiatAmount).reduce(_ + _) should be (fiatAmount)
    }
  }

  it must "add fiat fees to each step and to the required fiat amount" in
    new Fixture(paymentProcessor = new FixedFeeProcessor(0.5)) {
      forAnyAmounts { (bitcoinAmount, price, amounts) =>
        amounts.intermediateSteps.map(_.fiatFee).toSet should be (Set(0.5.EUR))
        amounts.fiatRequired.buyer - amounts.netFiatExchanged should
          be (0.5.EUR * amounts.intermediateSteps.size)
      }
    }

  it must "charge bitcoin transaction fees to the seller" in
    new Fixture(bitcoinFeeCalculator = new FixedBitcoinFee(0.001.BTC)) {
      forAnyAmounts { (bitcoinAmount, _, amounts) =>
        amounts.transactionFee should be (0.001.BTC)
        amounts.bitcoinRequired.buyer + bitcoinAmount shouldBe amounts.finalStep.depositSplit.buyer
        amounts.bitcoinRequired.seller - bitcoinAmount shouldBe
          (amounts.finalStep.depositSplit.seller + 0.003.BTC)
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

    def bitcoinStepSize(amounts: Amounts[Euros]): BitcoinAmount = {
      val price = amounts.netBitcoinExchanged.value / amounts.netFiatExchanged.value
      val stepSize = paymentProcessor.bestStepSize(Euro).value * price
      Bitcoin(stepSize.setScale(Bitcoin.precision, RoundingMode.UP))
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
