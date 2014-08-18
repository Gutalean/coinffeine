package coinffeine.gui.util

import scalafx.application.Platform

import coinffeine.model.event.CoinffeineAppEvent
import coinffeine.peer.api.EventHandler

/** A event handler ready to be used in JavaFX/ScalaFX concurrency model.
  *
  * JavaFX/ScalaFX have a very restrictive concurrency model to avoid race conditions
  * in its node tree. Thus, any event handler invoked from another thread other than
  * the application thread may cause a system exception.
  *
  * This class wraps a EventHandler object and ensures it is executed in the application
  * thread avoiding race conditions.
  *
  * @param delegate The event handler to be safely wrapped
  */
class FxEventHandler(delegate: EventHandler) extends EventHandler {

  override def isDefinedAt(x: CoinffeineAppEvent) = delegate.isDefinedAt(x)

  override def apply(event: CoinffeineAppEvent): Unit = {
    Platform.runLater {
      delegate(event)
    }
  }
}

object FxEventHandler {
  def apply(delegate: EventHandler) = new FxEventHandler(delegate)
}
