package coinffeine.peer.amounts

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
    val steps = stepsFor(bitcoinAmount, fiatAmount)
    val stepDeposit = steps.map(_.bitcoinAmount).reduce(_ max _)
    val deposits = Both(
      buyer = stepDeposit * DefaultAmountsCalculator.EscrowSteps.buyer,
      seller = bitcoinAmount + stepDeposit * DefaultAmountsCalculator.EscrowSteps.seller
    )
    val refunds = deposits.map(_ - stepDeposit)
    Exchange.Amounts(deposits, refunds, steps, bitcoinFeeCalculator.defaultTransactionFee)
  }

  private def stepsFor[C <: FiatCurrency](bitcoinAmount: BitcoinAmount,
                                          fiatAmount: CurrencyAmount[C]): Seq[StepAmounts[C]] = {
    val fiatStepAmounts = fiatStepAmountsFor(fiatAmount)
    val bitcoinStepAmounts: Seq[Bitcoin.Amount] = bitcoinStepAmountsFor(bitcoinAmount, fiatAmount, fiatStepAmounts)
    for ((fiatAmount, bitcoinAmount) <- fiatStepAmounts.zip(bitcoinStepAmounts))
    yield Exchange.StepAmounts(bitcoinAmount, fiatAmount, paymentProcessor.calculateFee(fiatAmount))
  }

  private def bitcoinStepAmountsFor[C <: FiatCurrency](bitcoinAmount: BitcoinAmount,
                                                       fiatAmount: CurrencyAmount[C],
                                                       fiatStepAmounts: Seq[CurrencyAmount[C]]): Seq[BitcoinAmount] = {
    val precision = BigDecimal(0.00000001)
    val (initialSteps, _) = fiatStepAmounts.init.foldLeft((Seq.empty[BitcoinAmount], BigDecimal(0))) {
      case ((bitcoinSteps, accumError), fiatStep) =>
        val (bitcoinStep, nextAccumError) =
          (fiatStep.value * bitcoinAmount.value / fiatAmount.value + accumError) /% precision
        (bitcoinSteps :+ Bitcoin.fromSatoshi(bitcoinStep.toBigInt()), nextAccumError)
    }

    val lastStep = bitcoinAmount - initialSteps.fold(Bitcoin.Zero)(_ + _)
    initialSteps :+ lastStep
  }

  private def fiatStepAmountsFor[C <: FiatCurrency](fiatAmount: CurrencyAmount[C]): Seq[CurrencyAmount[C]] = {
    val bestFiatSize = paymentProcessor.bestStepSize(fiatAmount.currency)
    val (exactSteps, remainingAmount) = fiatAmount.value /% bestFiatSize.value
    val wholeFiatSteps = Seq.fill(exactSteps.toIntExact)(bestFiatSize)
    val remainingFiatStep = Some(CurrencyAmount(remainingAmount, fiatAmount.currency))
    wholeFiatSteps ++ remainingFiatStep.filter(_.isPositive)
  }
}

private object DefaultAmountsCalculator {
  /** Amount of escrow deposits in terms of the amount exchanged on every step */
  private val EscrowSteps = Both(buyer = 2, seller = 1)
}
