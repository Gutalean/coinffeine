package coinffeine.gui.util

import java.util.Locale

import org.joda.time.format.{DateTimeFormat, PeriodFormatterBuilder}
import org.joda.time.{DateTime, Period}

class DateTimePrinter {

  private val shortElapsedFormatter = new PeriodFormatterBuilder()
    .appendHours().appendSuffix("h").appendSeparator(" ")
    .appendMinutes().appendSuffix("m").appendSeparator(" ")
    .appendLiteral(" ago")
    .printZeroNever()
    .toFormatter

  private val longElapsedFormatter = new PeriodFormatterBuilder()
    .appendDays().appendSuffix("d").appendSeparator(" ")
    .appendHours().appendSuffix("h").appendSeparator(" ")
    .appendLiteral(" ago")
    .printZeroNever()
    .toFormatter

  private val farElapsedFormatter = DateTimeFormat.forPattern("ddMMMyyyy").withLocale(Locale.US)

  private val dateTimeFormatter = DateTimeFormat.forPattern("MMMM dd, yyyy").withLocale(Locale.US)

  def printElapsed(timestamp: DateTime, elapsed: Period): String = {
    if (elapsed.getMonths > 0) farElapsedFormatter.print(timestamp)
    else if (elapsed.getDays > 0) longElapsedFormatter.print(elapsed)
    else if (elapsed.toStandardMinutes.getMinutes > 0) shortElapsedFormatter.print(elapsed)
    else "just now"
  }

  def printDate(timestamp: DateTime): String = dateTimeFormatter.print(timestamp)
}
