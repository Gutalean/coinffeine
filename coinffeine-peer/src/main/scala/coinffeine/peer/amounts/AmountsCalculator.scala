package coinffeine.peer.amounts

import coinffeine.model.currency._
import coinffeine.model.exchange.ActiveExchange
import coinffeine.model.market.Spread
import coinffeine.model.order.OrderRequest
import coinffeine.protocol.messages.brokerage.OrderMatch

trait AmountsCalculator {

  /** Compute the maximum fiat amount allowed per exchange. */
  def maxFiatPerExchange(currency: FiatCurrency): FiatAmount

  /** Compute amounts governing an exchange from the raw bitcoin and fiat exchanged */
  def exchangeAmountsFor(
      bitcoinAmount: BitcoinAmount,
      fiatAmount: FiatAmount): ActiveExchange.Amounts

  def exchangeAmountsFor(orderMatch: OrderMatch): ActiveExchange.Amounts =
    exchangeAmountsFor(orderMatch.bitcoinAmount.seller, orderMatch.fiatAmount.buyer)

  /** Estimate order amounts by assuming a single perfect match exchange
    *
    * @param order   Order whose amounts are to be estimated
    * @param spread  Current market spread relevant to market price orders
    * @return        An estimation if there is enough price information or [[None]]
    */
  def estimateAmountsFor(
      order: OrderRequest,
      spread: Spread): Option[ActiveExchange.Amounts]
}
