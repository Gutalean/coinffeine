package coinffeine.peer.amounts

import coinffeine.model.currency.{BitcoinAmount, CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange._

private[amounts] class DefaultExchangeAmountsCalculator extends ExchangeAmountsCalculator {
  import DefaultExchangeAmountsCalculator._

  override def amountsFor[C <: FiatCurrency](bitcoinAmount: BitcoinAmount,
                                             price: CurrencyAmount[C]) = {
    val step = Exchange.StepAmounts(
      bitcoinAmount = bitcoinAmount / Steps,
      fiatAmount = price * bitcoinAmount.value / Steps
    )
    val deposits = Both(
      buyer = step.bitcoinAmount * EscrowSteps.buyer,
      seller = bitcoinAmount + step.bitcoinAmount * EscrowSteps.seller
    )
    Exchange.Amounts[C](
      deposits,
      refunds = deposits.map(_ - step.bitcoinAmount),
      steps = Seq.fill(Steps)(step)
    )
  }
}

private object DefaultExchangeAmountsCalculator {
  /** Number of steps for the exchange */
  private val Steps = 10

  /** Amount of escrow deposits in terms of the amount exchanged on every step */
  private val EscrowSteps = Both(buyer = 2, seller = 1)
}
