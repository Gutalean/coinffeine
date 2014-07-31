package coinffeine.gui.application

import scalafx.geometry.Pos
import scalafx.util.Duration

import org.controlsfx.control.Notifications

import coinffeine.gui.util.FxEventHandler
import coinffeine.peer.api.CoinffeineApp
import coinffeine.peer.api.event.CoinffeineAppEvent

class NotificationManager(app: CoinffeineApp) {

  val notificationPosition = Pos.TOP_RIGHT
  val notificationDuration = Duration.apply(5000)

  app.observe(FxEventHandler { case event: CoinffeineAppEvent => notify(event) })

  private def notify(event: CoinffeineAppEvent): Unit = {
    val notif = Notifications.create()
      .text(event.summary)
      .position(notificationPosition)
      .hideAfter(notificationDuration)

    event.eventType match {
      case CoinffeineAppEvent.Info => notif.showInformation()
      case CoinffeineAppEvent.Warning => notif.showWarning()
      case CoinffeineAppEvent.Error => notif.showError()
      case CoinffeineAppEvent.Success => notif.showInformation()
    }
  }
}
