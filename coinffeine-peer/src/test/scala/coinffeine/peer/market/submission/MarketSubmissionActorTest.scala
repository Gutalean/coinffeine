package coinffeine.peer.market.submission

import scala.concurrent.duration._

import akka.testkit._

import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.peer.market.submission.SubmissionSupervisor.KeepSubmitting

class MarketSubmissionActorTest extends AkkaSpec {

  val market = Market(Euro)
  val resubmitInterval = 1.second.dilated
  val entry = OrderBookEntry.random(Bid, 1.BTC, Price(1000.EUR))

  "A market submission actor" should "resubmit orders at regular intervals" in new Fixture {
    actor ! KeepSubmitting(entry)
    forwarder.expectCreation() shouldBe Seq(Set(self -> entry))
    forwarder.expectStop()
    forwarder.expectCreation() shouldBe Seq(Set(self -> entry))
    forwarder.expectStop()
    forwarder.expectCreation() shouldBe Seq(Set(self -> entry))
    forwarder.expectStop()
  }

  it should "honor the timeout even when receiving other messages" in new Fixture {
    actor ! KeepSubmitting(entry)
    forwarder.expectCreation() shouldBe Seq(Set(self -> entry))
    expectNoMsg(resubmitInterval / 3)
    actor ! "some message"
    forwarder.expectStop(resubmitInterval)
    forwarder.expectCreation() shouldBe Seq(Set(self -> entry))
  }

  trait Fixture {
    protected val forwarder = new MockSupervisedActor
    protected val actor = system.actorOf(MarketSubmissionActor.props(
      market, (orders: SubmittingOrders[Euro.type]) => forwarder.props(orders), resubmitInterval)
    )
  }
}
