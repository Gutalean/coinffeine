package coinffeine.common.akka

import scala.concurrent.duration._

import akka.testkit._

import coinffeine.common.akka.test.AkkaSpec

class LimitedRateProxyTest extends AkkaSpec {

  val minTimeBetweenMessages = 1.second

  "A limited-rate proxy" should "forward the first message immediately" in new FreshProxy {
    proxy ! "first"
    probe.expectMsg(max = minTimeBetweenMessages / 2, obj = "first")
  }

  it should "preserve the original sender" in new FreshProxy {
    proxy ! "first"
    proxy ! "second"
    probe.expectMsg("first")
    probe.sender() shouldBe self
    probe.expectMsg("second")
    probe.sender() shouldBe self
  }

  it should "wait to send a second message" in new FreshProxy {
    proxy ! "first"
    proxy ! "second"
    probe.expectMsg("first")
    probe.expectNoMsg(minTimeBetweenMessages / 2)
    probe.expectMsg("second")
  }

  it should "send the last message of a burst" in new FreshProxy {
    proxy ! "first"
    1 to 10 foreach (proxy ! _)
    proxy ! "last"
    probe.expectMsg("first")
    probe.expectNoMsg(minTimeBetweenMessages / 2)
    probe.expectMsg("last")
  }

  trait FreshProxy {
    val probe = TestProbe()
    val proxy = system.actorOf(LimitedRateProxy.props(probe.ref, minTimeBetweenMessages.dilated))
  }
}
