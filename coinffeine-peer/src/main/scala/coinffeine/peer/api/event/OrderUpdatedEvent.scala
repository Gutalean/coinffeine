package coinffeine.peer.api.event

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.Order

/** An event triggered each time an order is updated. */
case class OrderUpdatedEvent(order: Order[FiatCurrency]) extends CoinffeineAppEvent
