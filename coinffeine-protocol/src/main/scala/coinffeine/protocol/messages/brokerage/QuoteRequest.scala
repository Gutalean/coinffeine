package coinffeine.protocol.messages.brokerage

import coinffeine.model.market.Market
import coinffeine.protocol.messages.PublicMessage

/** Used to ask about the current quote of bitcoin traded in a given currency */
case class QuoteRequest(market: Market) extends PublicMessage
