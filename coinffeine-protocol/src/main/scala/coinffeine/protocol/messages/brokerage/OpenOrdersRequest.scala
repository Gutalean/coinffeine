package coinffeine.protocol.messages.brokerage

import coinffeine.model.market.Market
import coinffeine.protocol.messages.PublicMessage

/** Used to ask about the current open orders of bitcoin traded in a given currency */
case class OpenOrdersRequest(market: Market) extends PublicMessage
