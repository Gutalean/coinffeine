package coinffeine.alarms.akka

import akka.actor.{Actor, Props}
import akka.testkit.TestProbe

import coinffeine.alarms.{Alarm, Severity}
import coinffeine.common.akka.test.AkkaSpec

class EventStreamReportingTest extends AkkaSpec {

  "Event stream reporting" should "receive alert when produced" in new Fixture {
    sendAlert()
    expectReceiveAlert()
  }

  it should "receive recover when produced" in new Fixture {
    sendRecover()
    expectReceiveRecover()
  }

  case object SomeAlarm extends Alarm {
    override val summary = "Oh no!"
    override val whatHappened = "More Lemmings!"
    override val howToFix = "Lead the lemmings to the level exit"
    override val severity = Severity.High
  }

  trait Fixture {

    val reporter = system.actorOf(Props(new Actor with EventStreamReporting {
      override def receive = {
        case "alert" => alert(SomeAlarm)
        case "recover" => recover(SomeAlarm)
      }
    }))

    val observer = {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[AlarmMessage])
      probe
    }

    def sendAlert(): Unit = { reporter ! "alert" }

    def sendRecover(): Unit = { reporter ! "recover" }

    def expectReceiveAlert(): Unit = {
      observer.expectMsg(AlarmMessage.Alert(SomeAlarm))
    }

    def expectReceiveRecover(): Unit = {
      observer.expectMsg(AlarmMessage.Recover(SomeAlarm))
    }
  }
}
