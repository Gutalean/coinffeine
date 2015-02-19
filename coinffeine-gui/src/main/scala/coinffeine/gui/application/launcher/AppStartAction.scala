package coinffeine.gui.application.launcher

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Await, Future}

import coinffeine.peer.api.CoinffeineApp

class AppStartAction(app: CoinffeineApp) {

  def apply(): Future[Unit] = Future {
    val appStart = app.start(30.seconds)
    Await.result(appStart, Duration.Inf)
  }(ExecutionContext.global)
}
