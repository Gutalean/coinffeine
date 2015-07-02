package coinffeine.common.time

import org.joda.time.{DateTime, Interval}

/** Helper class to identify calendar months */
object Month {

  def containing(instant: DateTime): Interval =
    new Interval(monthStartBefore(instant), monthStartAfter(instant))

  private def monthStartBefore(instant: DateTime): DateTime =
    instant.dayOfMonth().withMinimumValue()
        .withTimeAtStartOfDay()

  private def monthStartAfter(instant: DateTime): DateTime =
    instant.dayOfMonth().withMaximumValue()
        .withTimeAtStartOfDay()
        .plusDays(1)
}
