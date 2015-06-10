package coinffeine.common.akka.event

import org.scalatest.concurrent.Eventually

import coinffeine.common.akka.test.AkkaSpec

class EventObservedPropertyTest extends AkkaSpec with Eventually {

  "An event observed property" should "keep initial value before any event is sent" in new Fixture {
    prop.get shouldBe 0
  }

  it should "update property after publishing" in new Fixture {
    bus.publish("foobar", "7")
    eventually { prop.get shouldBe 7 }
  }

  trait Fixture {
    val bus = CoinffeineEventBusExtension(system)
    val prop = EventObservedProperty("foobar", 0) {
      case num: String => num.toInt
    }
  }
}
