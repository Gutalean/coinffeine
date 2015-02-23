package coinffeine.alarms

/** The ability to report alarm situations. */
trait AlarmReporting {

  /** Report that the system is under the effects of a new alarm. */
  def alert(a: Alarm): Unit

  /** Report that the system has been recovered from a previously alerted alarm. */
  def recover(a: Alarm): Unit
}
