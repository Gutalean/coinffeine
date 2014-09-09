package coinffeine.peer.amounts

import scala.math.BigDecimal.RoundingMode

import coinffeine.model.bitcoin.BitcoinFeeCalculator
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange._
import coinffeine.model.exchange._
import coinffeine.model.payment.PaymentProcessor

private[amounts] class DefaultAmountsCalculator(
    paymentProcessor: PaymentProcessor,
    bitcoinFeeCalculator: BitcoinFeeCalculator) extends AmountsCalculator {

  import DefaultAmountsCalculator._

  override def exchangeAmountsFor[C <: FiatCurrency](netBitcoinAmount: BitcoinAmount,
                                                     netFiatAmount: CurrencyAmount[C]) = {
    require(netBitcoinAmount.isPositive && netFiatAmount.isPositive)
    val stepsCalculator = new StepsAmountsCalculator(netBitcoinAmount, netFiatAmount)
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

  private class StepsAmountsCalculator[C <: FiatCurrency](netBitcoinAmount: BitcoinAmount,
                                                          netFiatAmount: CurrencyAmount[C]) {

    /** Best step size in fiat */
    private val bestFiatSize = paymentProcessor.bestStepSize(netFiatAmount.currency)

    private val txFee = bitcoinFeeCalculator.defaultTransactionFee

    /** Fiat amount exchanged per step: best amount except for the last step  */
    private val fiatStepAmounts: Seq[CurrencyAmount[C]] = {
      val (exactSteps, remainingAmount) = netFiatAmount.value /% bestFiatSize.value
      val wholeFiatSteps = Seq.fill(exactSteps.toIntExact)(bestFiatSize)
      val remainingFiatStep = Some(CurrencyAmount(remainingAmount, netFiatAmount.currency))
      wholeFiatSteps ++ remainingFiatStep.filter(_.isPositive)
    }

    /** Bitcoin amount exchanged per step. It mirrors the fiat amounts with a rounding error of at
      * most one satoshi. */
    private val bitcoinStepAmounts = {
      val numSteps = fiatStepAmounts.size
      val exactBitcoinStep = bestFiatSize.value * netBitcoinAmount.value / netFiatAmount.value
      val initialSteps = Seq.tabulate[BitcoinAmount](numSteps - 1) { index =>
        closestBitcoinAmount((index + 1) * exactBitcoinStep) -
          closestBitcoinAmount(index * exactBitcoinStep)
      }
      val lastStep = netBitcoinAmount - initialSteps.fold(Bitcoin.Zero)(_ + _)
      initialSteps :+ lastStep
    }

    /** How the amount to exchange is split per step */
    private val depositSplits: Seq[Both[BitcoinAmount]] = {
      cumulative(bitcoinStepAmounts).map(boughtAmount =>
        Both(boughtAmount + txFee, netBitcoinAmount - boughtAmount))
    }

    val maxBitcoinStepSize: BitcoinAmount = bitcoinStepAmounts.reduce(_ max _)

    val intermediateSteps: Seq[IntermediateStepAmounts[C]] = {
      val stepsProgress =
        for ((bitcoin, fiat) <- bitcoinStepAmounts.zip(fiatStepAmounts))
        yield Progress(bitcoin, fiat)
      for (((fiat, bitcoinSplit), progress) <- fiatStepAmounts.zip(depositSplits).zip(stepsProgress))
      yield Exchange.IntermediateStepAmounts(
        bitcoinSplit, fiat, paymentProcessor.calculateFee(fiat), progress)
    }

    def finalStep(deposits: Both[BitcoinAmount]): FinalStepAmounts[C] = FinalStepAmounts(
      depositSplit = Both(
        buyer = deposits.buyer + netBitcoinAmount + txFee,
        seller = deposits.seller - netBitcoinAmount
      ),
      progress = Progress(netBitcoinAmount, netFiatAmount)
    )

    private def closestBitcoinAmount(value: BigDecimal): BitcoinAmount =
      Bitcoin(value.setScale(Bitcoin.precision, RoundingMode.HALF_EVEN))

    private def cumulative[D <: Currency](amounts: Seq[CurrencyAmount[D]]): Seq[CurrencyAmount[D]] =
      amounts.tail.scan(amounts.head)(_ + _)
  }
}

private object DefaultAmountsCalculator {
  /** Amount of escrow deposits in terms of the amount exchanged on every step */
  private val EscrowSteps = Both(buyer = 2, seller = 1)

  private val HappyPathTransactions = 3
}
