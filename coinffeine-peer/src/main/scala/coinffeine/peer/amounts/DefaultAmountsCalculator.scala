package coinffeine.peer.amounts

import scala.math.BigDecimal.RoundingMode

import coinffeine.model.bitcoin.BitcoinFeeCalculator
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.{BitcoinAmount, CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange.Exchange.StepAmounts
import coinffeine.model.exchange._
import coinffeine.model.payment.PaymentProcessor

private[amounts] class DefaultAmountsCalculator(
    paymentProcessor: PaymentProcessor,
    bitcoinFeeCalculator: BitcoinFeeCalculator) extends AmountsCalculator {

  override def exchangeAmountsFor[C <: FiatCurrency](bitcoinAmount: BitcoinAmount,
                                                     fiatAmount: CurrencyAmount[C]) = {
    require(bitcoinAmount.isPositive && fiatAmount.isPositive)
    val steps = new StepsAmountsCalculator(bitcoinAmount, fiatAmount).steps
    val stepDeposit = steps.map(_.bitcoinAmount).reduce(_ max _)
    val deposits = Both(
      buyer = stepDeposit * DefaultAmountsCalculator.EscrowSteps.buyer,
      seller = bitcoinAmount + stepDeposit * DefaultAmountsCalculator.EscrowSteps.seller
    )
    val refunds = deposits.map(_ - stepDeposit)
    Exchange.Amounts(deposits, refunds, steps, bitcoinFeeCalculator.defaultTransactionFee)
  }

  private class StepsAmountsCalculator[C <: FiatCurrency](bitcoinAmount: BitcoinAmount,
                                                          fiatAmount: CurrencyAmount[C]) {

    private val bestFiatSize = paymentProcessor.bestStepSize(fiatAmount.currency)

    def steps: Seq[StepAmounts[C]] = {
      val fiatStepAmounts = fiatStepAmountsFor(fiatAmount)
      val bitcoinStepAmounts: Seq[Bitcoin.Amount] = bitcoinStepAmountsFor(fiatStepAmounts.size)
      for ((fiat, bitcoin) <- fiatStepAmounts.zip(bitcoinStepAmounts))
      yield Exchange.StepAmounts(bitcoin, fiat, paymentProcessor.calculateFee(fiat))
    }

    private def fiatStepAmountsFor(fiatAmount: CurrencyAmount[C]): Seq[CurrencyAmount[C]] = {
      val (exactSteps, remainingAmount) = fiatAmount.value /% bestFiatSize.value
      val wholeFiatSteps = Seq.fill(exactSteps.toIntExact)(bestFiatSize)
      val remainingFiatStep = Some(CurrencyAmount(remainingAmount, fiatAmount.currency))
      wholeFiatSteps ++ remainingFiatStep.filter(_.isPositive)
    }

    private def bitcoinStepAmountsFor(numSteps: Int): Seq[BitcoinAmount] = {
      val exactBitcoinStep = bestFiatSize.value * bitcoinAmount.value / fiatAmount.value
      val initialSteps = Seq.tabulate[BitcoinAmount](numSteps - 1) { index =>
        closestBitcoinAmount((index + 1) * exactBitcoinStep) -
          closestBitcoinAmount(index * exactBitcoinStep)
      }
      val lastStep = bitcoinAmount - initialSteps.fold(Bitcoin.Zero)(_ + _)
      initialSteps :+ lastStep
    }

    private def closestBitcoinAmount(value: BigDecimal): BitcoinAmount =
      Bitcoin(value.setScale(Bitcoin.precision, RoundingMode.HALF_EVEN))
  }
}

private object DefaultAmountsCalculator {
  /** Amount of escrow deposits in terms of the amount exchanged on every step */
  private val EscrowSteps = Both(buyer = 2, seller = 1)
}
