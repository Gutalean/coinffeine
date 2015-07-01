package coinffeine.common.time

import scala.concurrent.duration.FiniteDuration

import org.joda.time.{DateTime, ReadableDuration}

class ClockStub(initialTime: DateTime) extends Clock {

  private var time = initialTime

  def setAt(newTime: DateTime): Unit = {
    time = newTime
  }

  def advanceBy(duration: FiniteDuration): Unit = {
    time = time.plusMillis(duration.toMillis.toInt)
  }

  def advanceBy(duration: ReadableDuration): Unit = {
    time = time.plus(duration)
  }

  override def now(): DateTime = time
}

object ClockStub {
  def forCurrentTime() = new ClockStub(DateTime.now())
}
