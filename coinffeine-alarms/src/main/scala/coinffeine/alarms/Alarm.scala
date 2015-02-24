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
  def summary: String
  def description: String
  def severity: Severity
}
