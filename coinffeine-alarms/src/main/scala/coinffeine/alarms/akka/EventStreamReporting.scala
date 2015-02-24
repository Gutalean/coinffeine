package coinffeine.alarms.akka

import akka.actor.Actor

import coinffeine.alarms.{Alarm, AlarmReporting}

/** A alarm reporting behavior that notifies the alarms using Akka event stream. */
trait EventStreamReporting extends AlarmReporting { self: Actor =>

  override def alert(a: Alarm): Unit = {
    context.system.eventStream.publish(AlarmMessage.Alert(a))
  }

  override def recover(a: Alarm): Unit = {
    context.system.eventStream.publish(AlarmMessage.Recover(a))
  }
}
