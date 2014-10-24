package coinffeine.gui.notification

import coinffeine.model.market.NotStartedOrder
import coinffeine.peer.api.CoinffeineApp

class NotificationManager(app: CoinffeineApp) {

  private val orderNotification = PropertyMapNotification(app.network.orders) {
    case (id, _, order) if order.status == NotStartedOrder =>
      Some(InfoEvent(s"New order ${id.toShortString}"))

    case (id, Some(prevOrder), order) if prevOrder.status != order.status =>
      Some(InfoEvent(s"Order ${id.toShortString} is now ${order.status}"))

    case _ => None
  }
}
