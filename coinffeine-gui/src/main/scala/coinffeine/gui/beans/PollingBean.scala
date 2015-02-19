package coinffeine.gui.beans

import javafx.animation.{KeyFrame, Timeline}
import javafx.beans.value.ObservableValueBase
import javafx.event.{ActionEvent, EventHandler}
import javafx.util.Duration
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scalafx.animation.Animation

import coinffeine.gui.util.FxExecutor

/** A polling bean observable value.
  *
  * This class provides the means of a bean (`ObservableValue[T]`) that uses
  * a `=> Future[T]` lambda expression to poll a value. Such polling is used to
  * compute the bean value and invalidate it. In other words, the resulting
  * observable value represents the latest polled value.
  */
class PollingBean[T](interval: FiniteDuration, pollingAction: => Future[T])
  extends ObservableValueBase[Option[T]] with AutoCloseable {

  private var value: Option[T] = None

  private val reloader = {
    val timeline = new Timeline(new KeyFrame(
      Duration.millis(interval.toMillis),
      new EventHandler[ActionEvent] {
        override def handle(event: ActionEvent) = reloadData()
      }))
    timeline.setCycleCount(Animation.Indefinite)
    timeline.play()
    reloadData()
    timeline
  }

  override def getValue = value

  private def reloadData(): Unit = {
    pollingAction.onComplete(processNewValue)(FxExecutor.asContext)
  }

  private def processNewValue(newValue: Try[T]): Unit = {
    value = newValue.toOption
    fireValueChangedEvent()
  }

  override def close() = reloader.stop()
}

object PollingBean {

  def apply[T](interval: FiniteDuration)(pollingAction: => Future[T]): PollingBean[T] =
    new PollingBean(interval, pollingAction)
}
