package coinffeine.gui.application.operations

import java.util.Locale

import org.joda.time.{Period, DateTime}
import org.joda.time.format.{DateTimeFormat, PeriodFormatterBuilder}

class DateTimePrinter {

  private val shortPeriodFormatter = new PeriodFormatterBuilder()
    .appendHours().appendSuffix("h").appendSeparator(" ")
    .appendMinutes().appendSuffix("m").appendSeparator(" ")
    .appendLiteral(" ago")
    .printZeroNever()
    .toFormatter

  private val longPeriodFormatter = new PeriodFormatterBuilder()
    .appendDays().appendSuffix("d").appendSeparator(" ")
    .appendHours().appendSuffix("h").appendSeparator(" ")
    .appendLiteral(" ago")
    .printZeroNever()
    .toFormatter

  private val dateTimeFormatter = DateTimeFormat.forPattern("ddMMMyyyy").withLocale(Locale.US)

  def apply(timestamp: DateTime, elapsed: Period): String = {
    if (elapsed.getMonths > 0) dateTimeFormatter.print(timestamp)
    else if (elapsed.getDays > 0) longPeriodFormatter.print(elapsed)
    else if (elapsed.toStandardMinutes.getMinutes > 0) shortPeriodFormatter.print(elapsed)
    else "just now"
  }
}
