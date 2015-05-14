package coinffeine.peer.market.orders

import coinffeine.common.akka.test.MockActor.MockStopped
import coinffeine.model.exchange.AnyExchange
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

  it should "remember that an exchange was started" in new Fixture {
    givenInMarketOrder()
    gatewayProbe.relayMessageFromBroker(orderMatch)
    givenSuccessfulFundsBlocking(orderMatch.exchangeId)
    val Seq(exchange: AnyExchange) = exchangeActor.expectCreation()
    exchange.id shouldBe orderMatch.exchangeId

    restartOrder()
    exchangeActor.probe.expectMsgType[MockStopped]
    exchangeActor.expectCreation() shouldBe Seq(exchange)
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
    expectTerminated(actor)
    fundsBlocker.expectStop()

    startOrder()
    gatewayProbe.expectSubscription()
    gatewayProbe.relayMessageFromBroker(orderMatch)
    givenSuccessfulFundsBlocking(orderMatch.exchangeId)
  }
}
