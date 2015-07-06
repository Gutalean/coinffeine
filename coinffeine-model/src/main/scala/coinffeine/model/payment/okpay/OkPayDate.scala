package coinffeine.model.payment.okpay

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

object OkPayDate {
  val Format = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZoneUTC()

  def apply(date: DateTime): String = date.toString(Format)

  def unapply(date: String): Option[DateTime] =
    try Some(Format.parseDateTime(date))
    catch {
      case _: IllegalArgumentException => None
    }
}
