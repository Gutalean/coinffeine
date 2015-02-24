package coinffeine.gui.control

import scalafx.scene.control.Label
import scalafx.scene.image.ImageView

import coinffeine.alarms.Alarm
import coinffeine.gui.beans.Implicits._
import coinffeine.model.properties.Property

class AlarmSummaryWidget(alarms: Property[Set[Alarm]]) extends Label {

  styleClass += "alerts-summary"
  text <== alarms.map { a =>
    if (a.size > 0) s"${a.size} active alarms" else "No active alarms"
  }
  graphic <== alarms.map { a =>
    if (a.size == 0) new ImageView("/graphics/success-icon.png").delegate
    else new ImageView("/graphics/alert-icon.png").delegate
  }
}
