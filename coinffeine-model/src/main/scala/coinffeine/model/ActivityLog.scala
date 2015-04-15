package coinffeine.model

import org.joda.time.DateTime

/** Timestamped log of activities
  *
  * @constructor       Private constructor to maintain the invariant over {{{activities}}}
  * @param activities  Ordered event entries (most recent last)
  * @tparam Event      Type of the stored events
  */
case class ActivityLog[+Event] private (activities: Seq[ActivityLog.Entry[Event]]) {

  /** Record a new event
    *
    * @param event      Event to record
    * @param timestamp  When the event happened (defaults to the present moment)
    */
  @throws[IllegalArgumentException]("for non-increasing timestamps")
  def record[Event2 >: Event](event: Event2,
                              timestamp: DateTime = DateTime.now()): ActivityLog[Event2] = {
    require(activities.lastOption.forall(!_.timestamp.isAfter(timestamp)),
      s"Cannot add an event with timestamp $timestamp to $this")
    ActivityLog(activities :+ ActivityLog.Entry(event, timestamp))
  }

  /** Most recent entry if any */
  def mostRecent: Option[ActivityLog.Entry[Event]] = activities.lastOption

  /** Finds the last time something expressed as a predicate happened */
  def lastTime(pred: Event => Boolean): Option[DateTime] = activities.reverse.collectFirst {
    case ActivityLog.Entry(event, timestamp) if pred(event) => timestamp
  }
}

object ActivityLog {
  val empty = new ActivityLog[Nothing](Seq.empty)

  def apply[Event](event: Event, timestamp: DateTime = DateTime.now()): ActivityLog[Event] =
    empty.record(event, timestamp)

  case class Entry[+Event](event: Event, timestamp: DateTime)
}
