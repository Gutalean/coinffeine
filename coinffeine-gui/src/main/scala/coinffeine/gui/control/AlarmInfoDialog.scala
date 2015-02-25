package coinffeine.gui.control

import scalafx.Includes._
import scalafx.scene.Node
import scalafx.scene.control.{Button, Label}
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.VBox
import scalafx.stage.{Stage, StageStyle}

import coinffeine.alarms.Alarm
import coinffeine.gui.scene.{Stylesheets, CoinffeineScene}

class AlarmInfoDialog(alarm: Alarm) extends Stage(StageStyle.UTILITY) {

  private val whatHappenedSection = createSection("What happened?", alarm.whatHappened)

  private val howToFixItSection = createSection("How to fix it?", alarm.howToFix)

  private val acceptButton = new Button("Accept") {
    onMouseClicked = { e: MouseEvent =>  close() }
  }

  title = alarm.summary
  resizable = false
  scene = new CoinffeineScene(Stylesheets.Alarms) {
    root = new VBox {
      styleClass += "alarm-info"
      content = Seq(whatHappenedSection, howToFixItSection, acceptButton)
    }
  }

  private def createSection(title: String, body: String): Node = new VBox {
    styleClass += "section"
    content = Seq(new Label(title) { styleClass += "title" }, Label(body))
  }
}

object AlarmInfoDialog {
  def showFor(alarm: Alarm): Unit = {
    val dialog = new AlarmInfoDialog(alarm)
    dialog.showAndWait()
  }
}
