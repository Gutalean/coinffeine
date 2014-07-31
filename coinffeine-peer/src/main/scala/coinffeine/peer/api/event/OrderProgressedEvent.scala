package coinffeine.peer.api.event

import coinffeine.model.market.OrderId

/** An event reporting an order that has progressed. */
case class OrderProgressedEvent(order: OrderId,
                                prevProgress: Double,
                                newProgress: Double) extends CoinffeineAppEvent {

  override def eventType = CoinffeineAppEvent.Info

  override val summary =
    s"Order with ID $order has progressed from ${percent(prevProgress)} to ${percent(newProgress)}"

  private def percent(value: Double): String = "%.2f%%".format(value)
}
