package coinffeine.gui.control

import scalafx.Includes._
import scalafx.geometry.Orientation
import scalafx.scene.control.{Separator, Label}
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.{GridPane, VBox}

import coinffeine.alarms.Alarm
import coinffeine.gui.beans.Implicits._
import coinffeine.model.properties.Property

class AlarmDetailsPane(alarms: Property[Set[Alarm]]) extends VBox {

  styleClass += "alarm-details"

  alarms.bindToList(content) { activeAlarms =>
    val alarmsPresent = activeAlarms.size > 0
    val title = Label(
      if (!alarmsPresent) "There is no active alarm"
      else s"There are ${activeAlarms.size} active alarms")

    val sep = new Separator { orientation = Orientation.HORIZONTAL }

    val alarmList = new GridPane {
      styleClass += "alarm-list"
      add(new Label("Severity") { styleClass += "column-title" } , 0, 0)
      add(new Label("Description") { styleClass += "column-title" }, 1, 0)
      activeAlarms.zipWithIndex.foreach { case (a, i) =>
        val severity = Label(a.severity.toString.capitalize)
        val summary = new Label(a.summary) { styleClass += "clickable" }
        summary.onMouseClicked = { e: MouseEvent => AlarmInfoDialog.showFor(a) }
        add(severity, 0, i + 1)
        add(summary, 1, i + 1)
      }
    }

    if (alarmsPresent) Seq(title, sep, alarmList)
    else Seq(title)
  }

}
