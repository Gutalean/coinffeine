package coinffeine.peer.market.submission

import akka.actor.ActorRef

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.model.order.{Bid, Price}

class SubmissionTest extends UnitTest {

  val market = Market(Euro)
  val requester = ActorRef.noSender
  val orderBookEntry = OrderBookEntry.random(Bid, 10.BTC, Price(200.EUR))

  "A submission" should "not have duplicated order ids" in {
    val submissionEntry = Submission.Entry(requester, orderBookEntry)
    an [IllegalArgumentException] shouldBe thrownBy {
      Submission(market, Seq(submissionEntry, submissionEntry))
    }
  }

  it should "replace older versions of entries" in {
    val updatedEntry = orderBookEntry.copy[Euro.type](amount = 9.BTC)
    Submission.empty(market)
      .addEntry(requester, orderBookEntry)
      .addEntry(requester, updatedEntry)
      .entries should === (Seq(Submission.Entry(requester, updatedEntry)))
  }
}
