package coinffeine.peer.amounts

import coinffeine.model.currency.{BitcoinAmount, CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange._

private[amounts] class DefaultExchangeAmountsCalculator extends ExchangeAmountsCalculator {
  import DefaultExchangeAmountsCalculator._

  override def amountsFor[C <: FiatCurrency](bitcoinAmount: BitcoinAmount,
                                             price: CurrencyAmount[C]) = {
    val bitcoinStepAmount = bitcoinAmount / Steps
    val deposits = Both(
      buyer = bitcoinStepAmount * EscrowSteps.buyer,
      seller = bitcoinAmount + bitcoinStepAmount * EscrowSteps.seller
    )
    Exchange.Amounts[C](
      deposits,
      refunds = deposits.map(_ - bitcoinStepAmount),
      bitcoinExchanged = bitcoinAmount,
      fiatExchanged = price * bitcoinAmount.value,
      breakdown = Exchange.StepBreakdown(10)
    )
  }
}

private object DefaultExchangeAmountsCalculator {
  /** Number of steps for the exchange */
  private val Steps = 10

  /** Amount of escrow deposits in terms of the amount exchanged on every step */
  private val EscrowSteps = Both(buyer = 2, seller = 1)
}
