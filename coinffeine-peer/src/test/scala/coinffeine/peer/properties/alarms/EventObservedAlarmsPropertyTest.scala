package coinffeine.peer.properties.alarms

import org.scalatest.concurrent.Eventually

import coinffeine.alarms.akka.AlarmMessage
import coinffeine.alarms.{Alarm, Severity}
import coinffeine.common.akka.test.AkkaSpec

class EventObservedAlarmsPropertyTest extends AkkaSpec with Eventually {

  val property = new EventObservedAlarmsProperty()

  "Alarm reporter actor" should "include new alarm on alert" in {
    alert(Nazgul)
    eventually { property.get should contain (Nazgul) }
  }

  it should "update new alarms on alert" in {
    alert(Gollum)
    eventually {
      property.get should contain (Nazgul)
      property.get should contain (Gollum)
    }
  }

  it should "remove alarm on recover" in {
    recover(Nazgul)
    eventually { property.get shouldBe Set(Gollum) }
    recover(Gollum)
    eventually { property.get shouldBe 'empty }
  }

  case object Nazgul extends Alarm {
    override val summary = "NAZGUUUUL!!!!!"
    override val whatHappened = "A Dark Lord servant is here to kick your ass!"
    override val howToFix =
      "Distract him with a Hobbit from his back while you stab him with your sword. Good luck!"
    override val severity = Severity.High
  }

  case object Gollum extends Alarm {
    override val summary = "Gollum"
    override val whatHappened = "Oh no! Again this creepy creature!"
    override val howToFix = "Tie his leg with elven rope. And never untie it for any reason!"
    override val severity = Severity.Low
  }

  def alert(a: Alarm): Unit = { system.eventStream.publish(AlarmMessage.Alert(a)) }
  def recover(a: Alarm): Unit = { system.eventStream.publish(AlarmMessage.Recover(a)) }
}

