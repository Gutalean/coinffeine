package coinffeine.gui.control

import scalafx.animation.{Animation, ScaleTransition, Timeline}
import scalafx.scene.layout._
import scalafx.scene.shape.Rectangle
import scalafx.util.Duration

class Spinner(autoPlay: Boolean = false) extends HBox(spacing = 3) {
  styleClass += "spinner"
  private val bars = Seq.fill(3)(new Rectangle {
    styleClass += "bar"
    width = 3
    height = 6
  })
  private val animations = bars.zip(Spinner.AnimationDelays).map(Spinner.animate _ tupled)
  children = bars
  hgrow = Priority.Always
  vgrow = Priority.Always
  fillHeight = true

  if (autoPlay) {
    play()
  }

  def play(): Unit = {
    animations.foreach(_.play())
  }

  def stop(): Unit = {
    animations.foreach(_.stop())
  }
}

object Spinner {
  private val AnimationCycle = Duration(480)

  private val AnimationDelays = Seq.tabulate(3) { index =>
    Spinner.AnimationCycle * index / 3
  }

  private def animate(bar: Rectangle, animationDelay: Duration): Animation =
    new ScaleTransition(AnimationCycle, bar) {
      cycleCount = Timeline.Indefinite
      autoReverse = true
      delay = animationDelay
      byY = 1.5
    }
}
