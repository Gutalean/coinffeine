package coinffeine.peer.amounts

import coinffeine.model.currency.{BitcoinAmount, CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange.Exchange.Amounts

class AmountsCalculatorStub[C <: FiatCurrency](cannedValue: Amounts[C]) extends AmountsCalculator {
  override def exchangeAmountsFor[C2 <: FiatCurrency](
      bitcoinAmount: BitcoinAmount, fiatAmount: CurrencyAmount[C2]): Amounts[C2] = {
    if (cannedValue.currency == fiatAmount.currency) cannedValue.asInstanceOf[Amounts[C2]]
    else throw new UnsupportedOperationException(
      s"Only ${cannedValue.currency} is supported but ${fiatAmount.currency} was requested")
  }
}
