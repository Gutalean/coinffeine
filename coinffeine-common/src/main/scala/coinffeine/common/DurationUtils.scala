package coinffeine.common

import scala.concurrent.duration._

object DurationUtils {

  def requirePositive(duration: Duration, description: String): Unit = {
    require(isDefined(duration) && duration > 0.seconds, s"$description is not a positive duration")
  }

  def isDefined(duration: Duration): Boolean = !(duration eq Duration.Undefined)
}
