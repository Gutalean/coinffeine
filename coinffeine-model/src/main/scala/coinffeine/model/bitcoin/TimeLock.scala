package coinffeine.model.bitcoin

import org.bitcoinj.core.Transaction

sealed trait TimeLock

object TimeLock {
  case class ByHeigth(minHeigth: Long) extends TimeLock
  case class ByTimestamp(timestamp: Long) extends TimeLock

  def unapply(lockTime: Long): Option[TimeLock] =
    if (lockTime == 0) None
    else if (lockTime < Transaction.LOCKTIME_THRESHOLD) Some(ByHeigth(lockTime))
    else Some(ByTimestamp(lockTime))
}
