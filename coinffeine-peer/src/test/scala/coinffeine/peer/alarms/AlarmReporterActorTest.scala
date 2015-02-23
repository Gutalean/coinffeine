package coinffeine.peer.alarms

import scala.concurrent.duration._

import akka.testkit._
import org.scalatest.concurrent.Eventually

import coinffeine.alarms.akka.AlarmMessage
import coinffeine.alarms.{Alarm, Severity}
import coinffeine.common.akka.Service
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.peer.global.MutableGlobalProperties

class AlarmReporterActorTest extends AkkaSpec with Eventually {

  val global = new MutableGlobalProperties
  val reporter =system.actorOf(AlarmReporterActor.props(global))

  "Alarm reporter actor" should "ignore alarms before start" in {
    alert(Nazgul)
    after(100.millis.dilated) { global.alarms.get shouldBe 'empty }
  }

  it should "include new alarm on alert" in {
    startReporter()
    alert(Nazgul)
    eventually { global.alarms.get should contain (Nazgul) }
  }

  it should "update new alarms on alert" in {
    alert(Gollum)
    eventually {
      global.alarms.get should contain (Nazgul)
      global.alarms.get should contain (Gollum)
    }
  }

  it should "remove alarm on recover" in {
    recover(Nazgul)
    eventually { global.alarms.get shouldBe Set(Gollum) }
    recover(Gollum)
    eventually { global.alarms.get shouldBe 'empty }
  }

  it should "ignore alarms after stop" in {
    stopReporter()
    alert(Nazgul)
    after(100.millis.dilated) { global.alarms.get shouldBe 'empty }
  }

  case object Nazgul extends Alarm {
    override def summary = "NAZGUUUUL!!!!!"
    override def description = "A Dark Lord servant is here to kick your ass!"
    override def severity = Severity.High
  }

  case object Gollum extends Alarm {
    override def summary = "Gollum"
    override def description = "Oh no! Again this creepy creature!"
    override def severity = Severity.Low
  }

  def startReporter(): Unit = {
    reporter ! Service.Start({})
    expectMsg(Service.Started)
  }

  def stopReporter(): Unit = {
    reporter ! Service.Stop
    expectMsg(Service.Stopped)
  }

  def alert(a: Alarm): Unit = { system.eventStream.publish(AlarmMessage.Alert(a)) }
  def recover(a: Alarm): Unit = { system.eventStream.publish(AlarmMessage.Recover(a)) }
  def after(period: FiniteDuration)(action: => Unit): Unit = {
    Thread.sleep(period.toMillis)
    action
  }
}
