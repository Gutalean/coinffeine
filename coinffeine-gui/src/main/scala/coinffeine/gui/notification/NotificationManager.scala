package coinffeine.gui.notification

import coinffeine.model.market.OrderId
import coinffeine.peer.api.CoinffeineApp

class NotificationManager(app: CoinffeineApp) {

  private val orderNotification = PropertyMapNotification(app.network.orders) {
    case (id, None, order) =>
      Some(InfoEvent(s"New order ${shorten(id)}"))
    case (id, Some(prev), order) if prev.status != order.status =>
      Some(InfoEvent(s"Order ${shorten(id)} is now ${order.status}"))
    case _ =>
      None
  }

  private def shorten(id: OrderId): String = id.toString.takeWhile(_ != '-') + "..."
}
