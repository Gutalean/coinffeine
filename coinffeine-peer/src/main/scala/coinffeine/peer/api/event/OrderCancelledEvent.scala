package coinffeine.peer.api.event

import coinffeine.model.market.OrderId

/** An event triggered each time an order is cancelled. */
case class OrderCancelledEvent(orderId: OrderId) extends CoinffeineAppEvent

