package coinffeine.common.akka.event

import scala.concurrent.duration._

import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec

class CoinffeineEventBusTest extends AkkaSpec {

  "Coinffeine event bus" should "notify events exclusively to subscribers" in {
    val bus = new CoinffeineEventBus
    val actor1, actor2 = TestProbe()
    bus.subscribe(actor1.ref, "foo")
    bus.subscribe(actor2.ref, "bar")
    bus.publish("foo", "foo's message")
    bus.publish("bar", "bar's message")
    actor1.expectMsg("foo's message")
    actor1.expectNoMsg(50.millis)
    actor2.expectMsg("bar's message")
    actor2.expectNoMsg(50.millis)
  }

  it should "retain last message and publish upon subscription" in {
    val bus = new CoinffeineEventBus
    val actor = TestProbe()
    bus.publish("foo", "foo's message")
    bus.subscribe(actor.ref, "foo")
    actor.expectMsg("foo's message")
  }

  it should "not publish retained message if there is none" in {
    val bus = new CoinffeineEventBus
    val actor = TestProbe()
    bus.subscribe(actor.ref, "foo")
    actor.expectNoMsg(50.millis)
  }

  it should "not publish retained message if already subscribed" in {
    val bus = new CoinffeineEventBus
    val actor = TestProbe()
    bus.subscribe(actor.ref, "foo")
    bus.publish("foo", "message one")
    actor.expectMsg("message one")
    bus.subscribe(actor.ref, "foo") shouldBe false
    actor.expectNoMsg(50.millis)
  }
}
