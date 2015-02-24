package coinffeine.alarms.akka

import coinffeine.alarms.Alarm

/** An alarm message exchanged by Akka-based reporting actors. */
sealed trait AlarmMessage

object AlarmMessage {

  /** Report new alarm alert. */
  case class Alert(alarm: Alarm) extends AlarmMessage

  /** Report a new alarm recover. */
  case class Recover(alarm: Alarm) extends AlarmMessage
}
