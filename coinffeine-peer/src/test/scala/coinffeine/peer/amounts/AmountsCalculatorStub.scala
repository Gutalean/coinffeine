package coinffeine.peer.amounts

import coinffeine.model.currency.{Bitcoin, CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange.Exchange.Amounts

class AmountsCalculatorStub[C <: FiatCurrency](cannedValue: Amounts[C]) extends AmountsCalculator {

  override def maxFiatPerExchange[C2 <: FiatCurrency](currency: C2): CurrencyAmount[C2] =
    CurrencyAmount(50000, currency)

  override def exchangeAmountsFor[C2 <: FiatCurrency](
      bitcoinAmount: Bitcoin.Amount, fiatAmount: CurrencyAmount[C2]): Amounts[C2] = {
    if (cannedValue.currency == fiatAmount.currency) cannedValue.asInstanceOf[Amounts[C2]]
    else throw new UnsupportedOperationException(
      s"Only ${cannedValue.currency} is supported but ${fiatAmount.currency} was requested")
  }
}
