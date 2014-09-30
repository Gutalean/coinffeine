package coinffeine.peer.market

import scala.concurrent.duration._

import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.currency._
import coinffeine.protocol.gateway.MockGateway
import coinffeine.protocol.messages.brokerage._

class MarketInfoActorTest extends AkkaSpec {

  "A market info actor" should "retrieve a market quote" in new Fixture {
    actor ! MarketInfoActor.RequestQuote(eurMarket)
    messageGateway.expectForwardingToBroker(QuoteRequest(eurMarket))
    messageGateway.relayMessageFromBroker(sampleEurQuote)
    expectMsg(sampleEurQuote)
  }

  it should "group concurrent quote requests" in new Fixture {
    val concurrentRequester = TestProbe()

    actor ! MarketInfoActor.RequestQuote(eurMarket)
    messageGateway.expectForwardingToBroker(QuoteRequest(eurMarket))

    concurrentRequester.send(actor, MarketInfoActor.RequestQuote(eurMarket))
    messageGateway.expectNoMsg(100.millis)

    messageGateway.relayMessageFromBroker(sampleEurQuote)
    expectMsg(sampleEurQuote)
    concurrentRequester.expectMsg(sampleEurQuote)
  }

  it should "retrieve open orders" in new Fixture {
    actor ! MarketInfoActor.RequestOpenOrders(eurMarket)
    messageGateway.expectForwardingToBroker(OpenOrdersRequest(eurMarket))
    messageGateway.relayMessageFromBroker(sampleOpenOrders)
    expectMsg(sampleOpenOrders)
  }

  it should "group concurrent open orders requests" in new Fixture {
    val concurrentRequester = TestProbe()

    actor ! MarketInfoActor.RequestOpenOrders(eurMarket)
    messageGateway.expectForwardingToBroker(OpenOrdersRequest(eurMarket))

    concurrentRequester.send(actor, MarketInfoActor.RequestOpenOrders(eurMarket))
    messageGateway.expectNoMsg(100.millis)

    messageGateway.relayMessageFromBroker(sampleOpenOrders)
    expectMsg(sampleOpenOrders)
    concurrentRequester.expectMsg(sampleOpenOrders)
  }

  it should "handle different markets" in new Fixture {
    actor ! MarketInfoActor.RequestQuote(eurMarket)
    messageGateway.expectForwardingToBroker(QuoteRequest(eurMarket))

    val usdRequester = TestProbe()
    usdRequester.send(actor, MarketInfoActor.RequestQuote(usdMarket))
    messageGateway.expectForwardingToBroker(QuoteRequest(usdMarket))

    messageGateway.relayMessageFromBroker(sampleEurQuote)
    expectMsg(sampleEurQuote)

    messageGateway.relayMessageFromBroker(sampleUsdQuote)
    usdRequester.expectMsg(sampleUsdQuote)
  }

  it should "perform retries" in new Fixture {
    actor ! MarketInfoActor.RequestQuote(eurMarket)
    messageGateway.expectForwardingToBroker(QuoteRequest(eurMarket))
    messageGateway.expectNoMsg(100.millis)
    messageGateway.expectForwardingToBroker(
      payload = QuoteRequest(eurMarket),
      timeout = MarketInfoActor.RetryPolicy.timeout.duration
    )
    messageGateway.relayMessageFromBroker(sampleEurQuote)
    expectMsg(sampleEurQuote)
  }

  trait Fixture {
    val eurMarket = Market(Euro)
    val usdMarket = Market(UsDollar)
    val messageGateway = new MockGateway()
    val actor = system.actorOf(MarketInfoActor.props(messageGateway.ref))
    val sampleEurQuote = Quote(spread = 900.EUR -> 905.EUR, lastPrice = 904.EUR)
    val sampleUsdQuote = Quote(spread = 1000.USD -> 1010.USD, lastPrice = 1005.USD)
    val sampleOpenOrders = OpenOrders(PeerPositions.empty(eurMarket))
  }
}
