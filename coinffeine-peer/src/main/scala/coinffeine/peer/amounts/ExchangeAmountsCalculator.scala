package coinffeine.peer.amounts

import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange
import coinffeine.model.market.Order
import coinffeine.protocol.messages.brokerage.OrderMatch

trait ExchangeAmountsCalculator {

  def amountsFor[C <: FiatCurrency](bitcoinAmount: BitcoinAmount,
                                    price: CurrencyAmount[C]): Exchange.Amounts[C]

  def amountsFor[C <: FiatCurrency](order: Order[C]): Exchange.Amounts[C] =
    amountsFor(order.amount, order.price)

  def amountsFor(orderMatch: OrderMatch): Exchange.Amounts[_ <: FiatCurrency] =
    amountsFor(orderMatch.amount, orderMatch.price)
}
