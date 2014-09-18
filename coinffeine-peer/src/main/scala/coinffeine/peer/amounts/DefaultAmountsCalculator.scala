package coinffeine.peer.amounts

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

  override def exchangeAmountsFor[C <: FiatCurrency](grossBitcoinAmount: BitcoinAmount,
                                                     grossFiatAmount: CurrencyAmount[C]) = {
    type FiatAmount = CurrencyAmount[C]
    require(grossBitcoinAmount.isPositive && grossFiatAmount.isPositive)

    val txFee = bitcoinFeeCalculator.defaultTransactionFee
    val netBitcoinAmount = grossBitcoinAmount - txFee * 3
    require(netBitcoinAmount.isPositive, "No net bitcoin amount to exchange")

    val bestFiatStepSize = paymentProcessor.bestStepSize(grossFiatAmount.currency)

    /** Fiat amount exchanged per step and its fee: best amount except for the last step  */
    val fiatBreakdown: Seq[(FiatAmount, FiatAmount)] = {
      val bestFiatStepSizeWithFee = paymentProcessor.amountPlusFee(bestFiatStepSize)
      val (completeSteps, remainder) = grossFiatAmount /% bestFiatStepSizeWithFee
      val completedSteps =
        Seq.fill(completeSteps)(bestFiatStepSize -> paymentProcessor.calculateFee(bestFiatStepSize))
      val lastStep = if (remainder.isPositive) {
        val lastStepSize = paymentProcessor.amountMinusFee(remainder)
        require(lastStepSize.isPositive)
        Some(lastStepSize -> (remainder - lastStepSize))
      } else None
      completedSteps ++ lastStep
    }
    val netFiatAmount = fiatBreakdown.foldLeft(CurrencyAmount.zero(grossFiatAmount.currency))(_ + _._1)

    /** Bitcoin amount exchanged per step. It mirrors the fiat amounts with a rounding error of at
      * most one satoshi. */
    val bitcoinBreakdown: Seq[BitcoinAmount] = {
      val exactBitcoinStep = bestFiatStepSize.value * netBitcoinAmount.value / netFiatAmount.value
      val initialSteps = Seq.tabulate[BitcoinAmount](fiatBreakdown.size - 1) { index =>
        roundToTheSatoshi((index + 1) * exactBitcoinStep) -
          roundToTheSatoshi(index * exactBitcoinStep)
      }
      val lastStep = netBitcoinAmount - initialSteps.fold(Bitcoin.Zero)(_ + _)
      initialSteps :+ lastStep
    }

    val maxBitcoinStepSize: BitcoinAmount = bitcoinBreakdown.reduce(_ max _)

    /** How the amount to exchange is split per step */
    val depositSplits: Seq[Both[BitcoinAmount]] = {
      cumulative(bitcoinBreakdown).map(boughtAmount =>
        Both(boughtAmount + txFee, netBitcoinAmount - boughtAmount))
    }

    val intermediateSteps: Seq[IntermediateStepAmounts[C]] = {
      val stepProgress = for {
        (bitcoin, fiat) <- cumulative(bitcoinBreakdown).zip(cumulative(fiatBreakdown.map(s => s._1 + s._2)))
      } yield Exchange.Progress(bitcoin + txFee * HappyPathTransactions, fiat)
      (for {
        ((fiat, fiatFee), split, progress) <- (fiatBreakdown, depositSplits, stepProgress).zipped
      } yield Exchange.IntermediateStepAmounts[C](split, fiat, fiatFee, progress)).toSeq
    }

    val escrowAmounts = Both(
      buyer = maxBitcoinStepSize * EscrowSteps.buyer,
      seller = maxBitcoinStepSize * EscrowSteps.seller
    )

    val deposits = Both(
      buyer = DepositAmounts(input = escrowAmounts.buyer + txFee, output = escrowAmounts.buyer),
      seller = DepositAmounts(
        input = grossBitcoinAmount + escrowAmounts.seller,
        output = grossBitcoinAmount + escrowAmounts.seller - txFee
      )
    )

    val finalStep = Exchange.FinalStepAmounts[C](
      depositSplit = Both(
        buyer = netBitcoinAmount + escrowAmounts.buyer + txFee,
        seller = escrowAmounts.seller
      ),
      progress = Progress(grossBitcoinAmount, grossFiatAmount)
    )

    val refunds = deposits.map(_.input - maxBitcoinStepSize)

    Exchange.Amounts(
      grossBitcoinAmount, grossFiatAmount, deposits, refunds, intermediateSteps, finalStep)
  }

  private def roundToTheSatoshi(value: BigDecimal): BitcoinAmount =
    CurrencyAmount.closestAmount(value, Bitcoin)

  private def cumulative[D <: Currency](amounts: Seq[CurrencyAmount[D]]): Seq[CurrencyAmount[D]] =
    amounts.tail.scan(amounts.head)(_ + _)
}

private object DefaultAmountsCalculator {
  /** Amount of escrow deposits in terms of the amount exchanged on every step */
  private val EscrowSteps = Both(buyer = 2, seller = 1)

  private val HappyPathTransactions = 3
}
