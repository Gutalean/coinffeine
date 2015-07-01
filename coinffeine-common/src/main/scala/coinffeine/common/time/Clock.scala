package coinffeine.common.time

import org.joda.time.DateTime

/** Abstract source of timestamps */
trait Clock {
  def now(): DateTime
}

object SystemClock extends Clock {
  override def now() = DateTime.now()
}
