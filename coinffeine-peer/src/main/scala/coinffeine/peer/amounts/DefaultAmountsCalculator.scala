package coinffeine.peer.amounts

import scala.math.BigDecimal.RoundingMode

import coinffeine.model.bitcoin.BitcoinFeeCalculator
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.{BitcoinAmount, CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange.Exchange.{DepositAmounts, FinalStepAmounts, IntermediateStepAmounts}
import coinffeine.model.exchange._
import coinffeine.model.payment.PaymentProcessor

private[amounts] class DefaultAmountsCalculator(
    paymentProcessor: PaymentProcessor,
    bitcoinFeeCalculator: BitcoinFeeCalculator) extends AmountsCalculator {

  import DefaultAmountsCalculator._

  override def exchangeAmountsFor[C <: FiatCurrency](netBitcoinAmount: BitcoinAmount,
                                                     fiatAmount: CurrencyAmount[C]) = {
    require(netBitcoinAmount.isPositive && fiatAmount.isPositive)
    val stepsCalculator = new StepsAmountsCalculator(netBitcoinAmount, fiatAmount)
    val intermediateSteps = stepsCalculator.intermediateSteps
    val stepDeposit = stepsCalculator.maxBitcoinStepSize
    val deposits = Both(
      buyer = stepDeposit * EscrowSteps.buyer,
      seller = netBitcoinAmount + stepDeposit * EscrowSteps.seller
    )
    val txFee = bitcoinFeeCalculator.defaultTransactionFee
    val depositTransactionAmounts = Both(
      buyer = DepositAmounts(input = deposits.buyer + txFee, output = deposits.buyer),
      seller = DepositAmounts(
        input = deposits.seller + txFee * HappyPathTransactions,
        output = deposits.seller + txFee * (HappyPathTransactions - 1)
      )
    )
    val refunds = deposits.map(_ - stepDeposit)
    val grossBitcoinAmount = netBitcoinAmount + txFee * HappyPathTransactions
    val grossFiatAmount =
      stepsCalculator.intermediateSteps.map(s => s.fiatAmount + s.fiatFee).reduce(_ + _)
    Exchange.Amounts(grossBitcoinAmount, grossFiatAmount, depositTransactionAmounts,
      refunds, intermediateSteps, stepsCalculator.finalStep(deposits), txFee)
  }

  private class StepsAmountsCalculator[C <: FiatCurrency](bitcoinAmount: BitcoinAmount,
                                                          fiatAmount: CurrencyAmount[C]) {

    /** Best step size in fiat */
    private val bestFiatSize = paymentProcessor.bestStepSize(fiatAmount.currency)

    private val txFee = bitcoinFeeCalculator.defaultTransactionFee

    /** Fiat amount exchanged per step: best amount except for the last step  */
    private val fiatStepAmounts: Seq[CurrencyAmount[C]] = {
      val (exactSteps, remainingAmount) = fiatAmount.value /% bestFiatSize.value
      val wholeFiatSteps = Seq.fill(exactSteps.toIntExact)(bestFiatSize)
      val remainingFiatStep = Some(CurrencyAmount(remainingAmount, fiatAmount.currency))
      wholeFiatSteps ++ remainingFiatStep.filter(_.isPositive)
    }

    /** Bitcoin amount exchanged per step. It mirrors the fiat amounts with a rounding error of at
      * most one satoshi. */
    private val bitcoinStepAmounts = {
      val numSteps = fiatStepAmounts.size
      val exactBitcoinStep = bestFiatSize.value * bitcoinAmount.value / fiatAmount.value
      val initialSteps = Seq.tabulate[BitcoinAmount](numSteps - 1) { index =>
        closestBitcoinAmount((index + 1) * exactBitcoinStep) -
          closestBitcoinAmount(index * exactBitcoinStep)
      }
      val lastStep = bitcoinAmount - initialSteps.fold(Bitcoin.Zero)(_ + _)
      initialSteps :+ lastStep
    }

    /** How the amount to exchange is split per step */
    private val depositSplits: Seq[Both[BitcoinAmount]] = {
      val cumulativeAmounts = bitcoinStepAmounts.tail.scan(bitcoinStepAmounts.head)(_ + _)
      cumulativeAmounts.map(boughtAmount => Both(boughtAmount + txFee, bitcoinAmount - boughtAmount))
    }

    val maxBitcoinStepSize: BitcoinAmount = bitcoinStepAmounts.reduce(_ max _)

    val intermediateSteps: Seq[IntermediateStepAmounts[C]] = {
      for ((fiat, bitcoinSplit) <- fiatStepAmounts.zip(depositSplits))
      yield Exchange.IntermediateStepAmounts(bitcoinSplit, fiat, paymentProcessor.calculateFee(fiat))
    }

    def finalStep(deposits: Both[BitcoinAmount]): FinalStepAmounts[C] = FinalStepAmounts(Both(
      buyer = deposits.buyer + bitcoinAmount + txFee,
      seller = deposits.seller - bitcoinAmount
    ))

    private def closestBitcoinAmount(value: BigDecimal): BitcoinAmount =
      Bitcoin(value.setScale(Bitcoin.precision, RoundingMode.HALF_EVEN))
  }
}

private object DefaultAmountsCalculator {
  /** Amount of escrow deposits in terms of the amount exchanged on every step */
  private val EscrowSteps = Both(buyer = 2, seller = 1)

  private val HappyPathTransactions = 3
}
