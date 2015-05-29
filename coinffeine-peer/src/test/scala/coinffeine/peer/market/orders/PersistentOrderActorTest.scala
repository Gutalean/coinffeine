package coinffeine.peer.market.orders

import org.joda.time.DateTime

import coinffeine.model.exchange._
import coinffeine.model.order.OrderStatus
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.market.orders.archive.OrderArchive.{ArchiveOrder, OrderArchived}
import coinffeine.peer.market.submission.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import coinffeine.protocol.messages.handshake.ExchangeRejection

class PersistentOrderActorTest extends OrderActorTest {

  "A persistent order actor" should "remember that a match couldn't start because of funds shortage" in
    new Fixture {
      givenInMarketOrder()
      gatewayProbe.relayMessageFromBroker(orderMatch)
      givenFailedFundsBlocking()
      gatewayProbe.expectForwardingToBroker(
        ExchangeRejection(orderMatch.exchangeId, ExchangeRejection.UnavailableFunds))

      restartOrder()
      fundsBlocker.expectStop()

      expectNoMsg(idleTime)
      gatewayProbe.relayMessageFromBroker(orderMatch)
      fundsBlocker.expectCreation()
    }

  it should "recreate the funds blocker after a restart" in new Fixture {
    givenInMarketOrder()
    gatewayProbe.relayMessageFromBroker(orderMatch)
    fundsBlocker.expectCreation()

    restartOrder()
    fundsBlocker.expectStop()
    fundsBlocker.expectCreation()
  }

  it should "remember that an exchange was started" in new Fixture {
    givenInMarketOrder()
    gatewayProbe.relayMessageFromBroker(orderMatch)
    val Seq(exchange: AnyExchange) =
      givenSuccessfulFundsBlockingAndExchangeCreation(orderMatch.exchangeId)
    exchange.id shouldBe orderMatch.exchangeId

    restartOrder()
    exchangeActor.expectStop()
    exchangeActor.expectCreation() shouldBe Seq(exchange)
  }

  it should "remember how an exchange finished" in new Fixture {
    givenInMarketOrder()
    gatewayProbe.relayMessageFromBroker(orderMatch)
    val Seq(exchange: HandshakingExchange[_]) =
      givenSuccessfulFundsBlockingAndExchangeCreation(orderMatch.exchangeId)
    actor.tell(
      msg = ExchangeActor.ExchangeSuccess(completedExchange.copy(timestamp = DateTime.now())),
      sender = exchangeActor.ref
    )
    expectProperty(_.exchanges(exchange.id).isCompleted)
    properties.orders.set(order.id, null)
    exchangeActor.expectMsg(ExchangeActor.FinishExchange)

    restartOrder()
    expectProperty { _.status shouldBe OrderStatus.Completed }
    exchangeActor.expectStop()
    exchangeActor.expectNoMsg()
  }

  it should "remember that the order was cancelled" in new Fixture {
    givenInMarketOrder()
    submissionProbe.receiveWhile(idleTime) {
      case KeepSubmitting(_) =>
    }
    actor ! OrderActor.CancelOrder
    submissionProbe.expectMsg(StopSubmitting(order.id))

    restartOrder()
    expectNoMsg(idleTime)
    shouldRejectAnOrderMatch(ExchangeRejection.InvalidOrderMatch)
  }

  it should "delete its event-log after successfully being archived" in new Fixture {
    givenASuccessfulPerfectMatchExchange()
    archiveProbe.expectMsgType[ArchiveOrder]
    archiveProbe.send(actor, OrderArchived(order.id))
    submissionProbe.ignoreMsg { case _ => true }
    expectTerminated(actor)
    fundsBlocker.expectStop()
    expectNoMsg(idleTime)

    submissionProbe.ignoreNoMsg()
    startOrder()
    submissionProbe.expectMsgType[KeepSubmitting]
  }
}
