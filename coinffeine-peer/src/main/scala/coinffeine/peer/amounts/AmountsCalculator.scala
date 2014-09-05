package coinffeine.peer.amounts

import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange
import coinffeine.model.market.Order
import coinffeine.protocol.messages.brokerage.OrderMatch

trait AmountsCalculator {

  def exchangeAmountsFor[C <: FiatCurrency](
      bitcoinAmount: BitcoinAmount,
      fiatAmount: CurrencyAmount[C]): Exchange.Amounts[C]

  def exchangeAmountsFor[C <: FiatCurrency](order: Order[C]): Exchange.Amounts[C] =
    exchangeAmountsFor(order.amount, order.price.of(order.amount))

  def exchangeAmountsFor(orderMatch: OrderMatch): Exchange.Amounts[_ <: FiatCurrency] =
    exchangeAmountsFor(orderMatch.bitcoinAmount, orderMatch.fiatAmount)
}
