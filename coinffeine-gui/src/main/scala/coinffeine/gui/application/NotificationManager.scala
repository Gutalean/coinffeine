package coinffeine.gui.application

import scalafx.geometry.Pos
import scalafx.util.Duration

import org.controlsfx.control.Notifications

import coinffeine.gui.util.FxEventHandler
import coinffeine.model.event.NotifiableCoinffeineAppEvent
import coinffeine.peer.api.CoinffeineApp

class NotificationManager(app: CoinffeineApp) {

  val notificationPosition = Pos.TOP_RIGHT
  val notificationDuration = Duration.apply(5000)

  app.observe(FxEventHandler { case event: NotifiableCoinffeineAppEvent => notify(event) })

  private def notify(event: NotifiableCoinffeineAppEvent): Unit = {
    val notif = Notifications.create()
      .text(event.summary)
      .position(notificationPosition)
      .hideAfter(notificationDuration)

    event.eventType match {
      case NotifiableCoinffeineAppEvent.Info |
           NotifiableCoinffeineAppEvent.Success => notif.showInformation()
      case NotifiableCoinffeineAppEvent.Warning => notif.showWarning()
      case NotifiableCoinffeineAppEvent.Error => notif.showError()
    }
  }
}
