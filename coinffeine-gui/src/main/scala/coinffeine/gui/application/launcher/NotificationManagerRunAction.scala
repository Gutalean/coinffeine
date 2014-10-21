package coinffeine.gui.application.launcher

import scala.util.Success

import coinffeine.gui.notification.NotificationManager
import coinffeine.peer.api.CoinffeineApp

class NotificationManagerRunAction(app: CoinffeineApp) extends LaunchAction[Unit] {

  private var manager: Option[NotificationManager] = None

  override def apply() = Success {
    manager = Some(new NotificationManager(app))
  }
}
