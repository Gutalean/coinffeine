package coinffeine.alarms

/** An alarm reporting something wrong in the system.
  *
  * `Alarm` objects are used with `EventStreamReporting` trait to report anomalous
  * situations detected in the system. The `Alarm` instances are identified by
  * object instance in order to be able to report both alert and recover easily.
  * That means the most convenient way to declare a new alarm type is to extend
  * this trait with a case object.
  */
trait Alarm {
  /** A brief summary of the anomalous situation. */
  def summary: String

  /** A detailed description of what happened. This may be several lines of text. */
  def whatHappened: String

  /** A detailed description of how to fix the problem. This may be several lines of text. */
  def howToFix: String

  def severity: Severity
}
