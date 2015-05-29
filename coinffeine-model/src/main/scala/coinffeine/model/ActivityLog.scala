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
                              timestamp: DateTime = DateTime.now()): ActivityLog[Event2] =
    record(ActivityLog.Entry(event, timestamp))

  @throws[IllegalArgumentException]("for non-increasing timestamps")
  def record[Event2 >: Event](entry: ActivityLog.Entry[Event2]): ActivityLog[Event2] = {
    require(activities.lastOption.forall(!_.timestamp.isAfter(entry.timestamp)),
      s"Cannot add a '${entry.event}' event with timestamp ${entry.timestamp} to $this")
    ActivityLog(activities :+ entry)
  }

  /** Most recent entry if any */
  def mostRecent: Option[ActivityLog.Entry[Event]] = activities.lastOption

  /** Finds the last time something expressed as a predicate happened */
  def lastTime(pred: Event => Boolean): Option[DateTime] = activities.reverse.collectFirst {
    case ActivityLog.Entry(event, timestamp) if pred(event) => timestamp
  }
}

object ActivityLog {
  def empty[Event] = new ActivityLog[Event](Seq.empty)

  def apply[Event](event: Event, timestamp: DateTime = DateTime.now()): ActivityLog[Event] =
    empty.record(event, timestamp)

  def fromEvents[Event](events: (DateTime, Event)*): ActivityLog[Event] =
    ActivityLog(events.map { case (ts, event) => Entry(event, ts) })

  case class Entry[+Event](event: Event, timestamp: DateTime)
}
