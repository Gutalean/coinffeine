package coinffeine.peer.api.event

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{CancelledOrder, Order}

/** An event triggered each time an order is updated. */
@deprecated("Use OrderProgressedEvent and OrderStatusChangedEvent instead")
case class OrderUpdatedEvent(order: Order[FiatCurrency]) extends CoinffeineAppEvent {

  override def eventType = order.status match {
    case CancelledOrder(_) => CoinffeineAppEvent.Warning
    case _ => CoinffeineAppEvent.Info
  }

  override val summary = s"Order with ID ${order.id} has been updated"
}
