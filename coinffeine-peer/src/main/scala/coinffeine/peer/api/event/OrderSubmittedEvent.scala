package coinffeine.peer.api.event

import coinffeine.model.currency.FiatAmount
import coinffeine.model.market.OrderBookEntry

/** An event triggered each time a new order is submitted. */
case class OrderSubmittedEvent(order: OrderBookEntry[FiatAmount]) extends CoinffeineAppEvent

