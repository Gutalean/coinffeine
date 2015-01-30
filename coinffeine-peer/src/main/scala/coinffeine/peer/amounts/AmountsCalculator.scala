package coinffeine.peer.amounts

import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange
import coinffeine.model.market.Order
import coinffeine.protocol.messages.brokerage.OrderMatch

trait AmountsCalculator {

  /** Compute the maximum fiat amount allowed per exchange. */
  def maxFiatPerExchange[C <: FiatCurrency](currency: C): CurrencyAmount[C]

  /** Compute amounts governing an exchange from the raw bitcoin and fiat exchanged */
  def exchangeAmountsFor[C <: FiatCurrency](
      bitcoinAmount: Bitcoin.Amount,
      fiatAmount: CurrencyAmount[C]): Exchange.Amounts[C]

  def exchangeAmountsFor[C <: FiatCurrency](order: Order[C]): Exchange.Amounts[C] = {
    val limitPrice = order.price.toOption.getOrElse(
      throw new IllegalArgumentException("Impossible to calculate without a limit price"))
    exchangeAmountsFor(order.amount, limitPrice.of(order.amount))
  }

  def exchangeAmountsFor[C <: FiatCurrency](orderMatch: OrderMatch[C]): Exchange.Amounts[C] =
    exchangeAmountsFor(orderMatch.bitcoinAmount.seller, orderMatch.fiatAmount.buyer)
}
