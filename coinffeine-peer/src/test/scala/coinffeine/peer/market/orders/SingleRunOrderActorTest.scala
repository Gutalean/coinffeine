package coinffeine.peer.market.orders

import org.joda.time.DateTime

import coinffeine.model.exchange.ExchangeId
import coinffeine.model.order.OrderStatus
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.market.orders.archive.OrderArchive.{ArchiveOrder, CannotArchive, OrderArchived}
import coinffeine.peer.market.submission.SubmissionSupervisor.{InMarket, KeepSubmitting, StopSubmitting}
import coinffeine.protocol.messages.handshake.ExchangeRejection

class SingleRunOrderActorTest extends OrderActorTest {

  "An order actor" should "submit to the broker and receive submission status" in new Fixture {
    givenOfflineOrder()
    submissionProbe.send(actor, InMarket(entry))
    expectProperty { _ shouldBe 'inMarket }
  }

  it should "keep submitting to the broker until been cancelled" in new Fixture {
    givenOfflineOrder()
    expectNoMsg(idleTime)
    val reason = "some reason"
    actor ! OrderActor.CancelOrder
    submissionProbe.fishForMessage() {
      case KeepSubmitting(_) => false
      case StopSubmitting(order.id) => true
    }
    expectProperty { _.status shouldBe OrderStatus.Cancelled }
  }

  it should "reject order matches after being cancelled" in new Fixture {
    givenOfflineOrder()
    actor ! OrderActor.CancelOrder
    shouldRejectAnOrderMatch(ExchangeRejection.InvalidOrderMatch)
  }

  it should "stop submitting to the broker & report new status once matching is received" in
    new Fixture {
      givenInMarketOrder()
      gatewayProbe.relayMessageFromBroker(orderMatch)
      givenSuccessfulFundsBlockingAndExchangeCreation(orderMatch.exchangeId)
      submissionProbe.fishForMessage() {
        case StopSubmitting(orderId) if orderId == order.id => true
        case _ => false
      }
      expectProperty { _.status should not be OrderStatus.NotStarted }
      expectProperty { _.progress shouldBe 0.0 }
      actor.tell(
        msg = ExchangeActor.ExchangeSuccess(completedExchange.copy(timestamp = DateTime.now())),
        sender = exchangeActor.ref
      )
      exchangeActor.expectMsg(ExchangeActor.FinishExchange)
      expectProperty { _.status shouldBe OrderStatus.Completed }
    }

  it should "spawn an exchange upon matching" in new Fixture {
    givenInMarketOrder()
    gatewayProbe.relayMessageFromBroker(orderMatch)
    givenSuccessfulFundsBlockingAndExchangeCreation(orderMatch.exchangeId)
  }

  it should "accept new order matches if an exchange is active" in new Fixture {
    givenInMarketOrder()
    gatewayProbe.relayMessageFromBroker(halfOrderMatch)
    givenSuccessfulFundsBlockingAndExchangeCreation(halfOrderMatch.exchangeId)

    gatewayProbe.relayMessageFromBroker(halfOrderMatch.copy(exchangeId = ExchangeId.random()))
    gatewayProbe.expectNoMsg(idleTime)
  }

  it should "not reject resubmissions of already accepted order matches" in new Fixture {
    givenInMarketOrder()
    gatewayProbe.relayMessageFromBroker(orderMatch)
    givenSuccessfulFundsBlockingAndExchangeCreation(orderMatch.exchangeId)

    gatewayProbe.relayMessageFromBroker(orderMatch)
    gatewayProbe.expectNoMsg(idleTime)
  }

  it should "accept multiple order matches while confirming funds" in new Fixture {
    givenInMarketOrder()
    gatewayProbe.relayMessageFromBroker(halfOrderMatch)
    gatewayProbe.expectNoMsg(idleTime)
    gatewayProbe.relayMessageFromBroker(halfOrderMatch.copy(exchangeId = ExchangeId.random()))
    gatewayProbe.expectNoMsg(idleTime)
  }

  it should "archive and stop itself after success" in new Fixture {
    givenASuccessfulPerfectMatchExchange()
    archiveProbe.expectMsgType[ArchiveOrder]
    archiveProbe.send(actor, OrderArchived(order.id))
    expectTerminated(actor)
  }

  it should "not stop itself if archiving fails" in new Fixture {
    givenASuccessfulPerfectMatchExchange()
    archiveProbe.expectMsgType[ArchiveOrder]
    archiveProbe.send(actor, CannotArchive(order.id))
    expectAlive(actor)
  }
}
