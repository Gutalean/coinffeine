package coinffeine.peer.market

import scala.concurrent.duration._
import scala.util.Random

import akka.actor.Props
import akka.testkit.TestProbe
import org.scalatest.Inside

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.common.akka.{ServiceRegistry, ServiceRegistryActor}
import coinffeine.model.currency.Currency.{Euro, UsDollar}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.currency.Implicits._
import coinffeine.model.market.{Ask, Bid, OrderBookEntry, OrderId}
import coinffeine.model.network.{BrokerId, PeerId}
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.market.SubmissionSupervisor.{InMarket, KeepSubmitting, StopSubmitting}
import coinffeine.protocol.gateway.{MockGateway, MessageGateway}
import coinffeine.protocol.messages.brokerage.{Market, PeerPositions, PeerPositionsReceived}

class SubmissionSupervisorTest extends AkkaSpec with Inside {

  val constants = ProtocolConstants.Default.copy(
    orderExpirationInterval = 6.seconds,
    orderResubmitInterval = 4.seconds
  )
  val eurOrder1 = OrderBookEntry(OrderId("eurOrder1"), Bid, 1.3.BTC, 556.EUR)
  val eurOrder2 = OrderBookEntry(OrderId("eurOrder2"), Ask, 0.7.BTC, 640.EUR)
  val usdOrder = OrderBookEntry(OrderId("usdOrder"), Ask, 0.5.BTC, 500.USD)
  val noEurOrders = PeerPositions.empty(Market(Euro))
  val firstEurOrder = noEurOrders.addEntry(eurOrder1)
  val bothEurOrders = firstEurOrder.addEntry(eurOrder2)

  trait Fixture {
    val registryActor = system.actorOf(ServiceRegistryActor.props(), "registry-"+Random.nextInt())
    val registry = new ServiceRegistry(registryActor)
    val gateway = new MockGateway(PeerId("broker"))
    registry.register(MessageGateway.ServiceId, gateway.ref)

    val requester = TestProbe()
    val actor = system.actorOf(Props(new SubmissionSupervisor(constants)))
    actor ! SubmissionSupervisor.Initialize(registryActor)

    def keepSubmitting(entry: OrderBookEntry[_ <: FiatCurrency]): Unit = {
      requester.send(actor, KeepSubmitting(entry))
    }

    def stopSubmitting(entry: OrderBookEntry[_ <: FiatCurrency]): Unit = {
      requester.send(actor, StopSubmitting(entry.id))
    }

    def expectPeerPositionsForwarding(timeout: Duration,
                                      market: Market[_ <: FiatCurrency],
                                      entries: OrderBookEntry[_ <: FiatCurrency]*): Unit = {
      gateway.expectSubscription(timeout)
      gateway.expectForwardingPF(BrokerId, timeout) {
        case PeerPositions(`market`, entriesInMsg, nonce) =>
          entries.foreach(e => entriesInMsg should contain (e))
          gateway.relayMessageFromBroker(PeerPositionsReceived(nonce))
      }
    }

    def expectPeerPositionsForwarding(market: Market[_ <: FiatCurrency],
                                      entries: OrderBookEntry[_ <: FiatCurrency]*): Unit = {
      expectPeerPositionsForwarding(Duration.Undefined, market, entries: _*)
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
    expectPeerPositionsForwarding(Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    keepSubmitting(eurOrder2)
    expectPeerPositionsForwarding(Market(Euro), eurOrder1, eurOrder2)
    expectOrdersInMarket(eurOrder1, eurOrder2)
  }

  it must "keep resubmitting open orders to avoid them being discarded" in new Fixture {
    keepSubmitting(eurOrder1)
    expectPeerPositionsForwarding(Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    expectPeerPositionsForwarding(
      timeout = constants.orderExpirationInterval, Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    expectPeerPositionsForwarding(
      timeout = constants.orderExpirationInterval, Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
  }

  it must "group orders by target market" in new Fixture {
    keepSubmitting(eurOrder1)
    expectPeerPositionsForwarding(Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    keepSubmitting(usdOrder)
    expectPeerPositionsForwarding(Market(UsDollar), usdOrder)
    expectOrdersInMarket(usdOrder)
  }

  it must "keep resubmitting remaining orders after a cancellation" in new Fixture {
    keepSubmitting(eurOrder1)
    expectPeerPositionsForwarding(Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    keepSubmitting(eurOrder2)
    expectPeerPositionsForwarding(Market(Euro), eurOrder1, eurOrder2)
    expectOrdersInMarket(eurOrder1, eurOrder2)
    stopSubmitting(eurOrder2)
    expectPeerPositionsForwarding(Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    expectPeerPositionsForwarding(
      timeout = constants.orderExpirationInterval, Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    expectPeerPositionsForwarding(
      timeout = constants.orderExpirationInterval, Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
  }

  it must "keep silent if all the orders get cancelled" in new Fixture {
    keepSubmitting(eurOrder1)
    expectPeerPositionsForwarding(Market(Euro), eurOrder1)
    expectOrdersInMarket(eurOrder1)
    stopSubmitting(eurOrder1)
    expectPeerPositionsForwarding(Market(Euro))
    gateway.expectNoMsg(constants.orderExpirationInterval)
  }
}
