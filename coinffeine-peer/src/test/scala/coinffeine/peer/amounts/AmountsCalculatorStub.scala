package coinffeine.peer.amounts

import coinffeine.model.currency._
import coinffeine.model.exchange.ActiveExchange.Amounts
import coinffeine.model.market.Spread
import coinffeine.model.order.OrderRequest

class AmountsCalculatorStub extends AmountsCalculator {

  private var cannedExchangeAmounts = Seq.empty[Amounts]
  private var cannedEstimatedAmounts = Map.empty[(OrderRequest, Spread), Amounts]

  override def maxFiatPerExchange(currency: FiatCurrency): FiatAmount = currency(50000)

  override def exchangeAmountsFor(
      bitcoinAmount: BitcoinAmount, fiatAmount: FiatAmount): Amounts =
    cannedExchangeAmounts.find { value =>
      value.grossBitcoinExchanged == bitcoinAmount && value.grossFiatExchanged == fiatAmount
    }.getOrElse(throw new NoSuchElementException(
      s"No canned value for ($bitcoinAmount, $fiatAmount)"))

  override def estimateAmountsFor(order: OrderRequest, spread: Spread) =
    cannedEstimatedAmounts.get(order -> spread)

  def givenExchangeAmounts(amounts: Amounts): Unit = {
    cannedExchangeAmounts :+= amounts
  }

  def givenEstimateAmountsFor(order: OrderRequest, spread: Spread, amounts: Amounts): Unit = {
    cannedEstimatedAmounts += (order, spread) -> amounts
  }
}
