package coinffeine.peer.amounts

import scala.util.Try

import coinffeine.model.Both
import coinffeine.model.bitcoin.{BitcoinFeeCalculator, TransactionSizeFeeCalculator}
import coinffeine.model.currency._
import coinffeine.model.exchange.ActiveExchange._
import coinffeine.model.exchange.Exchange._
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.order.{Ask, Bid, OrderRequest}
import coinffeine.model.payment.okpay.OkPayPaymentProcessor
import coinffeine.peer.amounts.StepwisePaymentCalculator.Payment

class DefaultAmountsCalculator(
    stepwiseCalculator: StepwisePaymentCalculator,
    bitcoinFeeCalculator: BitcoinFeeCalculator) extends AmountsCalculator {

  def this() = this(
    new DefaultStepwisePaymentCalculator(OkPayPaymentProcessor), TransactionSizeFeeCalculator)

  override def maxFiatPerExchange(currency: FiatCurrency) =
    stepwiseCalculator.maximumBreakableFiatAmount(currency)

  override def exchangeAmountsFor(
      grossBitcoinAmount: BitcoinAmount,
      grossFiatAmount: FiatAmount) = {
    require(grossBitcoinAmount.isPositive && grossFiatAmount.isPositive,
      s"Gross amounts must be positive ($grossBitcoinAmount, $grossFiatAmount given)")
    val maxFiat = maxFiatPerExchange(grossFiatAmount.currency)
    require(grossFiatAmount <= maxFiat,
      s"Gross fiat amount ($grossFiatAmount) must not exceed the max fiat per exchange ($maxFiat)")

    val txFee = bitcoinFeeCalculator.defaultTransactionFee
    val netBitcoinAmount = grossBitcoinAmount - txFee * HappyPathTransactions
    require(netBitcoinAmount.isPositive, "No net bitcoin amount to exchange")

    val netFiatAmount = stepwiseCalculator.maximumPaymentWithGrossAmount(grossFiatAmount)
    require(netFiatAmount.isPositive, "No net fiat amount to exchange")

    /** Fiat amount exchanged per step and its fee: best amount except for the last step  */
    val fiatBreakdown: Seq[Payment] = stepwiseCalculator.breakIntoSteps(netFiatAmount)

    /** Bitcoin amount exchanged per step. It mirrors the fiat amounts with a rounding error of at
      * most one satoshi. */
    val bitcoinBreakdown: Seq[BitcoinAmount] = ProportionalAllocation
        .allocate(
          amount = netBitcoinAmount.units,
          weights = fiatBreakdown.map(_.netAmount.units).toVector)
        .map(Bitcoin.fromSatoshi)

    val maxBitcoinStepSize: BitcoinAmount = bitcoinBreakdown.max

    /** How the amount to exchange is split per step */
    val depositSplits: Seq[Both[BitcoinAmount]] = {
      cumulative(bitcoinBreakdown).map(boughtAmount =>
        Both(boughtAmount + txFee, netBitcoinAmount - boughtAmount))
    }

    val intermediateSteps: Seq[IntermediateStepAmounts] = {
      val stepProgress = cumulative(bitcoinBreakdown).map { case bitcoin =>
        Exchange.Progress(Both(buyer = bitcoin, seller = bitcoin + txFee * HappyPathTransactions))
      }
      for {
        (payment, split, progress) <- (fiatBreakdown, depositSplits, stepProgress).zipped
      } yield IntermediateStepAmounts(split, payment.netAmount, payment.fee, progress)
    }.toSeq

    val escrowAmounts = Both(
      buyer = maxBitcoinStepSize * DefaultAmountsCalculator.EscrowSteps.buyer,
      seller = maxBitcoinStepSize * DefaultAmountsCalculator.EscrowSteps.seller
    )

    val deposits = Both(
      buyer = DepositAmounts(input = escrowAmounts.buyer + txFee, output = escrowAmounts.buyer),
      seller = DepositAmounts(
        input = grossBitcoinAmount + escrowAmounts.seller,
        output = grossBitcoinAmount + escrowAmounts.seller - txFee
      )
    )

    val finalStep = FinalStepAmounts(
      depositSplit = Both(
        buyer = netBitcoinAmount + escrowAmounts.buyer + txFee,
        seller = escrowAmounts.seller
      ),
      progress = Progress(Both(buyer = netBitcoinAmount, seller = grossBitcoinAmount))
    )

    val refunds = deposits.map(_.input - maxBitcoinStepSize)

    Amounts(grossBitcoinAmount, grossFiatAmount, deposits, refunds, intermediateSteps, finalStep)
  }

  override def estimateAmountsFor(
      order: OrderRequest, spread: Spread): Option[Amounts] =
    order.estimatedPrice(spread).flatMap { price =>
      val grossBitcoinAmount = order.orderType match {
        case Bid => order.amount + bitcoinFeeCalculator.defaultTransactionFee * HappyPathTransactions
        case Ask => order.amount
      }
      val grossFiatAmount = order.orderType match {
        case Bid => price.of(order.amount)
        case Ask => stepwiseCalculator.requiredAmountToPay(price.of(order.amount))
      }
      Try(exchangeAmountsFor(grossBitcoinAmount, grossFiatAmount)).toOption
    }

  private def cumulative(amounts: Seq[BitcoinAmount]): Seq[BitcoinAmount] =
    amounts.tail.scan(amounts.head)(_ + _)
}

private object DefaultAmountsCalculator {
  /** Amount of escrow deposits in terms of the amount exchanged on every step */
  private val EscrowSteps = Both(buyer = 2, seller = 1)
}
