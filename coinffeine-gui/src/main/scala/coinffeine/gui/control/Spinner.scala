package coinffeine.gui.control

import scala.math._

import scalafx.Includes._
import scalafx.animation._
import scalafx.event.ActionEvent
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
  private val AnimationCycle = Duration(1500)
  private val FramesPerCycle = 15

  private val AnimationDelays = Seq.tabulate(3) { index =>
    Spinner.AnimationCycle * index / 6
  }

  private def animate(bar: Rectangle, animationDelay: Duration): Animation = new Timeline {
    cycleCount = Animation.Indefinite
    keyFrames = Seq.tabulate(FramesPerCycle) { i =>
      val instant = Duration(i * AnimationCycle.toMillis / FramesPerCycle)
      makeFrame(bar, instant, animationDelay)
    }
  }

  private def makeFrame(bar: Rectangle, instant: Duration, delay: Duration): KeyFrame = {
    val maxT = AnimationCycle.toMillis
    val middleT = 0.5 * maxT
    val t = (instant.toMillis + maxT - delay.toMillis) % maxT
    val progress = smoothStep(if (t < middleT) 2.0 * t / maxT else 2.0 - (2.0 * t / maxT))
    KeyFrame(instant, values = Set.empty, onFinished = { _: ActionEvent =>
      bar.scaleY = 0.5 + 1.5 * progress
    })
  }

  private def smoothStep(t: Double): Double = 6.0 * pow(t, 5) - 15.0 * pow(t, 4) + 10.0 * pow(t, 3)
}
