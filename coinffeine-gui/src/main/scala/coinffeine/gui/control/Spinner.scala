package coinffeine.gui.control

import scalafx.animation.{ScaleTransition, Timeline}
import scalafx.scene.layout._
import scalafx.scene.shape.Rectangle
import scalafx.util.Duration

class Spinner extends HBox(spacing = 3) {
  styleClass += "spinner"
  content = Seq.tabulate(3) { index =>
    Spinner.bar(Spinner.AnimationCycle * index / 3)
  }
  hgrow = Priority.Always
  vgrow = Priority.Always
  fillHeight = true
}

object Spinner {
  private val AnimationCycle = Duration(480)

  private def bar(animationDelay: Duration): Rectangle = new Rectangle {
    styleClass += "bar"
    width = 5
    height = 11
    new ScaleTransition(AnimationCycle, this) {
      cycleCount = Timeline.Indefinite
      autoReverse = true
      delay = animationDelay
      byY = 2
    }.play()
  }
}
