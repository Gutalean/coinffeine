package coinffeine.common.akka.event

import akka.actor.{ActorRef, Props, Actor}
import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec

class CoinffeineEventBusExtensionTest extends AkkaSpec {

  class Producer extends Actor with CoinffeineEventProducer {
    override def receive = {
      case "publish" => publish("foobar", "product")
    }
  }

  class Consumer(probe: ActorRef) extends Actor with CoinffeineEventConsumer {
    override def receive = {
      case "subscribe" => subscribe("foobar")
      case other => probe ! other
    }
  }

  "Coinffeine event bus extension" should "allow producers and consumers to communicate" in {
    val probe = TestProbe()
    val producer = system.actorOf(Props(new Producer))
    val consumer = system.actorOf(Props(new Consumer(probe.ref)))
    consumer ! "subscribe"
    producer ! "publish"
    probe.expectMsg("product")
  }
}
