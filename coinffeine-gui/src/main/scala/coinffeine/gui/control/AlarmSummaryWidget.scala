package coinffeine.gui.control

import scalafx.scene.image.Image

import coinffeine.alarms.Alarm
import coinffeine.common.properties.Property
import coinffeine.gui.beans.Implicits._

class AlarmSummaryWidget(alarms: Property[Set[Alarm]])
  extends ImgLabel(AlarmSummaryWidget.OkImg, "") {

  styleClass += "alarm-summary"
  text <== alarms.map { a =>
    if (a.nonEmpty) s"${a.size} active alarms" else "No active alarms"
  }
  image <== alarms.map { a =>
    if (a.isEmpty) AlarmSummaryWidget.OkImg.delegate
    else AlarmSummaryWidget.WarningImg.delegate
  }
}

object AlarmSummaryWidget {
  val OkImg = new Image("/graphics/success-icon.png")
  val WarningImg = new Image("/graphics/alert-icon.png")
}
