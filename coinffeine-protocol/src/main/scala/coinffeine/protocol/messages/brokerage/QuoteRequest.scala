package coinffeine.protocol.messages.brokerage

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.Market
import coinffeine.protocol.messages.PublicMessage

/** Used to ask about the current quote of bitcoin traded in a given currency */
case class QuoteRequest(market: Market[_ <: FiatCurrency]) extends PublicMessage
