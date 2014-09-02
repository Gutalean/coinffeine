package coinffeine.peer.amounts

import coinffeine.model.bitcoin.BitcoinFeeCalculator
import coinffeine.model.currency.{BitcoinAmount, CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange._
import coinffeine.model.payment.PaymentProcessor

private[amounts] class DefaultAmountsCalculator(
    paymentProcessor: PaymentProcessor,
    bitcoinFeeCalculator: BitcoinFeeCalculator) extends AmountsCalculator {

  import DefaultAmountsCalculator._

  override def exchangeAmountsFor[C <: FiatCurrency](
      bitcoinAmount: BitcoinAmount,
      fiatAmount: CurrencyAmount[C]) = {
    val stepFiatAmount = fiatAmount / Steps
    val step = Exchange.StepAmounts(
      bitcoinAmount = bitcoinAmount / Steps,
      fiatAmount = stepFiatAmount,
      fiatFee = paymentProcessor.calculateFee(stepFiatAmount)
    )
    val deposits = Both(
      buyer = step.bitcoinAmount * EscrowSteps.buyer,
      seller = bitcoinAmount + step.bitcoinAmount * EscrowSteps.seller
    )
    Exchange.Amounts[C](
      deposits,
      refunds = deposits.map(_ - step.bitcoinAmount),
      steps = Seq.fill(Steps)(step),
      transactionFee = bitcoinFeeCalculator.defaultTransactionFee
    )
  }
}

private object DefaultAmountsCalculator {
  /** Number of steps for the exchange */
  private val Steps = 10

  /** Amount of escrow deposits in terms of the amount exchanged on every step */
  private val EscrowSteps = Both(buyer = 2, seller = 1)
}
