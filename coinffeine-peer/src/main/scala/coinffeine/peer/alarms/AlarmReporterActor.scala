package coinffeine.peer.alarms

import akka.actor.{Props, Actor}
import com.typesafe.scalalogging.StrictLogging

import coinffeine.alarms.akka.AlarmMessage
import coinffeine.common.akka.ServiceLifecycle
import coinffeine.peer.global.MutableGlobalProperties

class AlarmReporterActor(global: MutableGlobalProperties) extends Actor
    with ServiceLifecycle[Unit] with StrictLogging {

  override def onStart(args: Unit) = {
    context.system.eventStream.subscribe(self, classOf[AlarmMessage])
    BecomeStarted(processingAlarms)
  }

  override protected def onStop() = {
    context.system.eventStream.unsubscribe(self)
    BecomeStopped
  }

  def processingAlarms: Receive = {
    case AlarmMessage.Alert(alarm) if !global.alarms.get.contains(alarm) =>
      logger.info("Received a new alert for alarm {}", alarm)
      global.alarms.update(_ + alarm)
    case AlarmMessage.Recover(alarm) if global.alarms.get.contains(alarm) =>
      logger.info("Received a recovery for alarm {}", alarm)
      global.alarms.update(_ - alarm)
  }
}

object AlarmReporterActor {

  def props(global: MutableGlobalProperties): Props = Props(new AlarmReporterActor(global))

  trait Component { this: MutableGlobalProperties.Component =>
    def alarmReporterProps = props(globalProperties)
  }
}
