package coinffeine.peer.api.event

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.Order

/** An event triggered each time a new order is submitted. */
case class OrderSubmittedEvent(order: Order[FiatCurrency]) extends CoinffeineAppEvent {

  override val eventType = CoinffeineAppEvent.Success
  override val summary = s"New order with ID ${order.id} has been submitted"
}

