package coinffeine.peer.amounts

import coinffeine.model.currency._
import coinffeine.model.exchange.ActiveExchange.Amounts
import coinffeine.model.market.Spread
import coinffeine.model.order.OrderRequest

class AmountsCalculatorStub(cannedValues: Amounts*) extends AmountsCalculator {

  override def maxFiatPerExchange(currency: FiatCurrency): FiatAmount = currency(50000)

  override def exchangeAmountsFor(
      bitcoinAmount: BitcoinAmount, fiatAmount: FiatAmount): Amounts =
    cannedValues.find { value =>
      value.grossBitcoinExchanged == bitcoinAmount && value.grossFiatExchanged == fiatAmount
    }.getOrElse(throw new UnsupportedOperationException(
      s"No canned value for ($bitcoinAmount, $fiatAmount)"))

  override def estimateAmountsFor(order: OrderRequest, spread: Spread) = ???
}
