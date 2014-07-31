package coinffeine.peer.api.event

import coinffeine.model.market.OrderId

/** An event reporting an order that has progressed. */
case class OrderProgressedEvent(order: OrderId,
                                prevProgress: Double,
                                newProgress: Double) extends CoinffeineAppEvent
