package coinffeine.peer.market.submission

import scala.concurrent.duration._

import akka.testkit.TestProbe
import org.scalatest.Inside

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.model.network.BrokerId
import coinffeine.model.order.{OrderId, Ask, Bid, Price}
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.market.submission.SubmissionSupervisor.{InMarket, KeepSubmitting, StopSubmitting}
import coinffeine.protocol.gateway.MockGateway
import coinffeine.protocol.messages.brokerage.{PeerPositions, PeerPositionsReceived}

class SubmissionSupervisorTest extends AkkaSpec with Inside {

  val eurOrder1 = OrderBookEntry(OrderId("eurOrder1"), Bid, 1.3.BTC, Price(556.EUR))
  val eurOrder2 = OrderBookEntry(OrderId("eurOrder2"), Ask, 0.7.BTC, Price(640.EUR))
  val usdOrder = OrderBookEntry(OrderId("usdOrder"), Ask, 0.5.BTC, Price(500.USD))
  val noEurOrders = PeerPositions.empty(Market(Euro))
  val firstEurOrder = noEurOrders.addEntry(eurOrder1)
  val bothEurOrders = firstEurOrder.addEntry(eurOrder2)

  trait Fixture {
    def orderAcknowledgeTimeout = 1.minute
    val constants = ProtocolConstants.Default.copy(
      orderExpirationInterval = 6.seconds,
      orderResubmitInterval = 4.seconds,
      orderAcknowledgeTimeout = orderAcknowledgeTimeout
    )
    val gateway = new MockGateway()

    val requester = TestProbe()
    val actor = system.actorOf(SubmissionSupervisor.props(gateway.ref, constants))

    def keepSubmitting(entry: OrderBookEntry[_ <: FiatCurrency]): Unit = {
      requester.send(actor, KeepSubmitting(entry))
    }

    def stopSubmitting(entry: OrderBookEntry[_ <: FiatCurrency]): Unit = {
      requester.send(actor, StopSubmitting(entry.id))
    }

    def expectPeerPositionsForwarding(
        market: Market[_ <: FiatCurrency],
        entries: OrderBookEntry[_ <: FiatCurrency]*): Unit = {
      gateway.expectForwardingToPF(BrokerId) {
        case PeerPositions(`market`, entriesInMsg, nonce) =>
          entries.foreach(e => entriesInMsg should contain (e))
      }
    }

    def expectPeerPositionsForwardingAndAcknowledgeThem(
        timeout: Duration,
        market: Market[_ <: FiatCurrency],
        entries: OrderBookEntry[_ <: FiatCurrency]*): Unit = {
      gateway.expectForwardingToPF(BrokerId, timeout) {
        case PeerPositions(`market`, entriesInMsg, nonce) =>
          entries.foreach(e => entriesInMsg should contain (e))
          gateway.relayMessageFromBroker(PeerPositionsReceived(nonce))
      }
    }

    def expectPeerPositionsForwardingAndAcknowledgeThem(
        market: Market[_ <: FiatCurrency], entries: OrderBookEntry[_ <: FiatCurrency]*): Unit = {
      expectPeerPositionsForwardingAndAcknowledgeThem(Duration.Undefined, market, entries: _*)
    }

    def expectOrdersInMarket[C <: FiatCurrency](entries: OrderBookEntry[C]*): Unit = {
      requester.expectMsgAllOf(entries.map(e => InMarket(e)): _*)
    }
  }

  "An order submission actor" must "keep silent as long as there is no open orders" in new Fixture {
    gateway.expectNoMsg()
  }

  it must "submit all orders as soon as a new one is open" in new Fixture {
    keepSubmitting(eurOrder1)
    expectPeerPositionsForwardingAndAcknowledgeThem(Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    keepSubmitting(eurOrder2)
    expectPeerPositionsForwardingAndAcknowledgeThem(Market(Euro), eurOrder1, eurOrder2)
    expectOrdersInMarket(eurOrder1, eurOrder2)
  }

  it must "resubmit orders after a timeout" in new Fixture {
    override def orderAcknowledgeTimeout = 1.second
    keepSubmitting(eurOrder1)
    expectPeerPositionsForwarding(Market(Euro), eurOrder1)
    expectPeerPositionsForwarding(Market(Euro), eurOrder1)
    expectPeerPositionsForwardingAndAcknowledgeThem(Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
  }

  it must "keep resubmitting open orders to avoid them being discarded" in new Fixture {
    keepSubmitting(eurOrder1)
    expectPeerPositionsForwardingAndAcknowledgeThem(Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    expectPeerPositionsForwardingAndAcknowledgeThem(
      timeout = constants.orderExpirationInterval, Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    expectPeerPositionsForwardingAndAcknowledgeThem(
      timeout = constants.orderExpirationInterval, Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
  }

  it must "group orders by target market" in new Fixture {
    keepSubmitting(eurOrder1)
    expectPeerPositionsForwardingAndAcknowledgeThem(Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    keepSubmitting(usdOrder)
    expectPeerPositionsForwardingAndAcknowledgeThem(Market(UsDollar), usdOrder)
    expectOrdersInMarket(usdOrder)
  }

  it must "keep resubmitting remaining orders after a cancellation" in new Fixture {
    keepSubmitting(eurOrder1)
    expectPeerPositionsForwardingAndAcknowledgeThem(Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    keepSubmitting(eurOrder2)
    expectPeerPositionsForwardingAndAcknowledgeThem(Market(Euro), eurOrder1, eurOrder2)
    expectOrdersInMarket(eurOrder1, eurOrder2)
    stopSubmitting(eurOrder2)
    expectPeerPositionsForwardingAndAcknowledgeThem(Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    expectPeerPositionsForwardingAndAcknowledgeThem(
      timeout = constants.orderExpirationInterval, Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    expectPeerPositionsForwardingAndAcknowledgeThem(
      timeout = constants.orderExpirationInterval, Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
  }

  it must "keep silent if all the orders get cancelled" in new Fixture {
    keepSubmitting(eurOrder1)
    expectPeerPositionsForwardingAndAcknowledgeThem(Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    stopSubmitting(eurOrder1)
    expectPeerPositionsForwardingAndAcknowledgeThem(Market(Euro))
    gateway.expectNoMsg(constants.orderExpirationInterval)
  }
}
