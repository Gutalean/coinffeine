package coinffeine.protocol.messages.brokerage

import coinffeine.model.market.Market
import coinffeine.protocol.messages.PublicMessage

/** Used to ask about the current open orders of bitcoin traded in a given currency */
case class OpenOrders(orders: PeerPositions) extends PublicMessage

object OpenOrders {
  def empty(market: Market): OpenOrders = OpenOrders(PeerPositions.empty(market))
}
