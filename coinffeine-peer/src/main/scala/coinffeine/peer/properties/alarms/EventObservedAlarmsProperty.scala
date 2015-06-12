package coinffeine.peer.properties.alarms

import scala.concurrent.ExecutionContext

import akka.actor.{Actor, ActorSystem, Props}
import com.typesafe.scalalogging.StrictLogging

import coinffeine.alarms.Alarm
import coinffeine.alarms.akka.AlarmMessage
import coinffeine.common.properties.{MutableProperty, Property}

class EventObservedAlarmsProperty(implicit system: ActorSystem) extends Property[Set[Alarm]] {

  private val delegate = new MutableProperty[Set[Alarm]](Set.empty)

  override def get = delegate.get

  override def onChange(handler: OnChangeHandler)(implicit executor: ExecutionContext) =
    delegate.onChange(handler)(executor)

  private class AlarmReporterActor extends Actor with StrictLogging {

    override def preStart() = {
      context.system.eventStream.subscribe(self, classOf[AlarmMessage])
    }

    override def receive: Receive = {
      case AlarmMessage.Alert(alarm) if !delegate.get.contains(alarm) =>
        logger.info("Received a new alert for alarm {}", alarm)
        delegate.update(_ + alarm)
      case AlarmMessage.Recover(alarm) if delegate.get.contains(alarm) =>
        logger.info("Received a recovery for alarm {}", alarm)
        delegate.update(_ - alarm)
    }
  }

  system.actorOf(Props(new AlarmReporterActor))
}
