package coinffeine.model.event

import coinffeine.model.market.{CompletedOrder, CancelledOrder, OrderId, OrderStatus}

/** An event reporting that the status of an order has been changed.
  *
  * @param order        The ID of the order whose status has been changed
  * @param prevStatus   The previous status of the order
  * @param newStatus    The new status of the order
  */
case class OrderStatusChangedEvent(order: OrderId,
                                   prevStatus: OrderStatus,
                                   newStatus: OrderStatus) extends NotifiableCoinffeineAppEvent {

  override def eventType = (prevStatus, newStatus) match {
    case (_, CancelledOrder(_)) => NotifiableCoinffeineAppEvent.Warning
    case (_, CompletedOrder) => NotifiableCoinffeineAppEvent.Success
    case _ => NotifiableCoinffeineAppEvent.Info
  }

  override val summary = s"Order is $newStatus"
  override val description = s"Status of $order has been changed from $prevStatus to $newStatus"
}
