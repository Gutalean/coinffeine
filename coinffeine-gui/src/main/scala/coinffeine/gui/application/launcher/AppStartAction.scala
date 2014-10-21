package coinffeine.gui.application.launcher

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

import coinffeine.peer.api.CoinffeineApp

class AppStartAction(app: CoinffeineApp) {

  def apply() = Try {
    val appStart = app.start(30.seconds)
    Await.result(appStart, Duration.Inf)
  }
}
