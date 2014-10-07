package coinffeine.gui.notification

import scalafx.geometry.Pos
import scalafx.util.Duration

import org.controlsfx.control.Notifications

trait Event {

  def summary: String

  def showNotification(pos: Pos = Event.DefaultPos, duration: Duration = Event.DefaultDuration) = {
    show(Notifications.create()
      .text(summary)
      .position(pos)
      .hideAfter(duration.delegate))
  }

  protected def show(notif: Notifications): Unit
}

object Event {
  val DefaultPos = Pos.TopRight
  val DefaultDuration: Duration = new Duration(Duration.apply(5000))
}

case class InfoEvent(summary: String) extends Event {
  protected def show(notif: Notifications): Unit = {
    notif.showInformation()
  }
}

case class WarningEvent(summary: String) extends Event  {
  protected def show(notif: Notifications): Unit = {
    notif.showWarning()
  }
}

case class ErrorEvent(summary: String) extends Event  {
  protected def show(notif: Notifications): Unit = {
    notif.showError()
  }
}
