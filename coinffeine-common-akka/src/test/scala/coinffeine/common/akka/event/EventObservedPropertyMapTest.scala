package coinffeine.common.akka.event

import org.scalatest.concurrent.{Eventually, IntegrationPatience}

import coinffeine.common.akka.test.AkkaSpec

class EventObservedPropertyMapTest extends AkkaSpec with Eventually with IntegrationPatience {

  "Event observed property map" should "be empty if no command is received" in new Fixture {
    prop.content shouldBe 'empty
  }

  it should "put after corresponding command is received" in new Fixture {
    bus.publish("foobar", Add("7"))
    eventually { prop("7") shouldBe 7 }
  }

  it should "remove after corresponding command is received" in new Fixture {
    bus.publish("foobar", Add("7"))
    eventually { prop("7") shouldBe 7 }
    bus.publish("foobar", Remove("7"))
    eventually { prop.content shouldBe 'empty }
  }

  trait Fixture {
    case class Add(n: String)
    case class Remove(n: String)

    val bus = CoinffeineEventBusExtension(system)
    val prop = EventObservedPropertyMap[String, Int]("foobar") {
      case Add(num) => EventObservedPropertyMap.Put(num, num.toInt)
      case Remove(num) => EventObservedPropertyMap.Remove(num)
    }
  }
}
