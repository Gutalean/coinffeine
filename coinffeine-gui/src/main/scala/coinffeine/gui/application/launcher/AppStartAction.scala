package coinffeine.gui.application.launcher

import scala.concurrent.Future

import coinffeine.peer.api.CoinffeineApp

class AppStartAction(app: => CoinffeineApp) {
  def apply(): Future[Unit] = app.start()
}
