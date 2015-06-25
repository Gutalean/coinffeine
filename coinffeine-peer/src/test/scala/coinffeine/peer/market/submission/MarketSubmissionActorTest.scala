package coinffeine.peer.market.submission

import scala.concurrent.duration._

import akka.testkit._

import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.model.order.{Bid, Price}
import coinffeine.peer.market.submission.SubmissionSupervisor.KeepSubmitting

class MarketSubmissionActorTest extends AkkaSpec {

  private val market = Market(Euro)
  private val resubmitInterval = 1.second.dilated
  private val entry = OrderBookEntry.random(Bid, 1.BTC, Price(1000.EUR))
  private val expectedSubmission = Submission.empty(market).addEntry(self, entry)

  "A market submission actor" should "resubmit orders at regular intervals" in new Fixture {
    actor ! KeepSubmitting(entry)
    forwarder.expectCreation() shouldBe Seq(expectedSubmission)
    forwarder.expectStop()
    forwarder.expectCreation() shouldBe Seq(expectedSubmission)
    forwarder.expectStop()
    forwarder.expectCreation() shouldBe Seq(expectedSubmission)
    forwarder.expectStop()
  }

  it should "discard old entries when newer ones are passed" in new Fixture {
    actor ! KeepSubmitting(entry)
    val updatedEntry = entry.copy(amount = entry.amount - 0.1.BTC)
    actor ! KeepSubmitting(updatedEntry)
    forwarder.expectCreation() shouldBe Seq(expectedSubmission)
    forwarder.expectStop()
    forwarder.expectCreation() shouldBe Seq(expectedSubmission.addEntry(self, updatedEntry))
    forwarder.expectStop()
  }

  it should "honor the timeout even when receiving other messages" in new Fixture {
    actor ! KeepSubmitting(entry)
    forwarder.expectCreation() shouldBe Seq(expectedSubmission)
    expectNoMsg(resubmitInterval / 3)
    actor ! "some message"
    forwarder.expectStop(resubmitInterval)
    forwarder.expectCreation() shouldBe Seq(expectedSubmission)
  }

  trait Fixture {
    protected val forwarder = new MockSupervisedActor
    protected val actor = system.actorOf(MarketSubmissionActor.props(
      market, (orders: Submission) => forwarder.props(orders), resubmitInterval)
    )
  }
}
