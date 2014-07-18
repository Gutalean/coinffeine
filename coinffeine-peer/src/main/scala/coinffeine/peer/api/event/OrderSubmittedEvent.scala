package coinffeine.peer.api.event

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.Order

/** An event triggered each time a new order is submitted. */
case class OrderSubmittedEvent(order: Order[FiatCurrency]) extends CoinffeineAppEvent

