package coinffeine.gui.notification

import coinffeine.peer.api.CoinffeineApp

class NotificationManager(app: CoinffeineApp) {

  private val orderNotification = PropertyMapNotification(app.network.orders) {
    case (id, None, order) =>
      Some(InfoEvent(s"New order ${id.toShortString}"))
    case (id, Some(prev), order) if prev.status != order.status =>
      Some(InfoEvent(s"Order ${id.toShortString} is now ${order.status}"))
    case _ =>
      None
  }
}
