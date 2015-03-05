package coinffeine.gui.control

import scalafx.scene.image.Image

import coinffeine.alarms.Alarm
import coinffeine.gui.beans.Implicits._
import coinffeine.model.properties.Property

class AlarmSummaryWidget(alarms: Property[Set[Alarm]]) extends ImgLabel(AlarmSummaryWidget.OkImg, "") {

  styleClass += "alarm-summary"
  text <== alarms.map { a =>
    if (a.size > 0) s"${a.size} active alarms" else "No active alarms"
  }
  image <== alarms.map { a =>
    if (a.size == 0) AlarmSummaryWidget.OkImg.delegate
    else AlarmSummaryWidget.WarningImg.delegate
  }
}

object AlarmSummaryWidget {
  val OkImg = new Image("/graphics/success-icon.png")
  val WarningImg = new Image("/graphics/alert-icon.png")
}
