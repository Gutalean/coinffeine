package coinffeine.gui.control

import scalafx.Includes._
import scalafx.geometry.Orientation
import scalafx.scene.control.{Label, Separator}
import scalafx.scene.layout.{GridPane, VBox}

import coinffeine.alarms.Alarm
import coinffeine.common.properties.Property
import coinffeine.gui.beans.Implicits._

class AlarmDetailsPane(alarms: Property[Set[Alarm]]) extends VBox {

  styleClass += "alarm-details"

  alarms.bindToList(children) { activeAlarms =>
    val title = Label(activeAlarms.size match {
      case 0 => "There is no active alarm"
      case 1 => "There is 1 active alarm"
      case _ => "There are ${activeAlarms.size} active alarms"
    })

    val sep = new Separator { orientation = Orientation.HORIZONTAL }

    val alarmList = new GridPane {
      styleClass += "alarm-list"
      add(new Label("Severity") { styleClass += "column-title" } , 0, 0)
      add(new Label("Description") { styleClass += "column-title" }, 1, 0)
      activeAlarms.zipWithIndex.foreach { case (a, i) =>
        val severity = Label(a.severity.toString.capitalize)
        val summary = new Label(a.summary) { styleClass += "clickable" }
        summary.onMouseClicked = () => { AlarmInfoDialog.showFor(a) }
        add(severity, 0, i + 1)
        add(summary, 1, i + 1)
      }
    }

    if (activeAlarms.nonEmpty) Seq(title, sep, alarmList)
    else Seq(title)
  }

}
