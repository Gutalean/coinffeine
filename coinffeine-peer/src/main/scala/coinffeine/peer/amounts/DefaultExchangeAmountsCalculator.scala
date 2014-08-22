package coinffeine.peer.amounts

import coinffeine.model.currency.{BitcoinAmount, CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange._

private[amounts] class DefaultExchangeAmountsCalculator extends ExchangeAmountsCalculator {
  override def amountsFor[C <: FiatCurrency](bitcoinAmount: BitcoinAmount, price: CurrencyAmount[C]) =
    Exchange.Amounts[C](
      bitcoinExchanged = bitcoinAmount,
      fiatExchanged = price * bitcoinAmount.value,
      breakdown = Exchange.StepBreakdown(10)
    )
}
