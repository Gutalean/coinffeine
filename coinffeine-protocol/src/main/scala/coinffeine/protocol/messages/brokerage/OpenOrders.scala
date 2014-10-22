package coinffeine.protocol.messages.brokerage

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.Market
import coinffeine.protocol.messages.PublicMessage

/** Used to ask about the current open orders of bitcoin traded in a given currency */
case class OpenOrders[C <: FiatCurrency](orders: PeerPositions[C]) extends PublicMessage

object OpenOrders {
  def empty[C <: FiatCurrency](market: Market[C]): OpenOrders[C] =
    OpenOrders(PeerPositions.empty(market))
}
