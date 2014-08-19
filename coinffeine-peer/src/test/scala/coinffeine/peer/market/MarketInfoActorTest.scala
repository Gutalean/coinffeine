package coinffeine.peer.market

import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.currency.Currency.{Euro, UsDollar}
import coinffeine.model.currency.Implicits._
import coinffeine.peer.GlobalServices
import coinffeine.protocol.messages.brokerage._

class MarketInfoActorTest extends AkkaSpec {

  "A market info actor" should "retrieve a market quote" in new Fixture {
    actor ! MarketInfoActor.RequestQuote(eurMarket)
    messageGateway.expectForwarding(QuoteRequest(eurMarket), broker)
    messageGateway.relayMessageFromBroker(sampleEurQuote)
    expectMsg(sampleEurQuote)
  }

  it should "group concurrent quote requests" in new Fixture {
    val concurrentRequester = TestProbe()

    actor ! MarketInfoActor.RequestQuote(eurMarket)
    messageGateway.expectForwarding(QuoteRequest(eurMarket), broker)

    concurrentRequester.send(actor, MarketInfoActor.RequestQuote(eurMarket))
    messageGateway.expectNoMsg()

    messageGateway.relayMessageFromBroker(sampleEurQuote)
    expectMsg(sampleEurQuote)
    concurrentRequester.expectMsg(sampleEurQuote)
  }

  it should "retrieve open orders" in new Fixture {
    actor ! MarketInfoActor.RequestOpenOrders(eurMarket)
    messageGateway.expectForwarding(OpenOrdersRequest(eurMarket), broker)
    messageGateway.relayMessageFromBroker(sampleOpenOrders)
    expectMsg(sampleOpenOrders)
  }

  it should "group concurrent open orders requests" in new Fixture {
    val concurrentRequester = TestProbe()

    actor ! MarketInfoActor.RequestOpenOrders(eurMarket)
    messageGateway.expectForwarding(OpenOrdersRequest(eurMarket), broker)

    concurrentRequester.send(actor, MarketInfoActor.RequestOpenOrders(eurMarket))
    messageGateway.expectNoMsg()

    messageGateway.relayMessageFromBroker(sampleOpenOrders)
    expectMsg(sampleOpenOrders)
    concurrentRequester.expectMsg(sampleOpenOrders)
  }

  it should "handle different markets" in new Fixture {
    actor ! MarketInfoActor.RequestQuote(eurMarket)
    messageGateway.expectForwarding(QuoteRequest(eurMarket), broker)

    val usdRequester = TestProbe()
    usdRequester.send(actor, MarketInfoActor.RequestQuote(usdMarket))
    messageGateway.expectForwarding(QuoteRequest(usdMarket), broker)

    messageGateway.relayMessageFromBroker(sampleEurQuote)
    expectMsg(sampleEurQuote)

    messageGateway.relayMessageFromBroker(sampleUsdQuote)
    usdRequester.expectMsg(sampleUsdQuote)
  }

  trait Fixture extends GlobalServices {
    val eurMarket = Market(Euro)
    val usdMarket = Market(UsDollar)
    val actor = system.actorOf(MarketInfoActor.props)
    val sampleEurQuote = Quote(spread = 900.EUR -> 905.EUR, lastPrice = 904.EUR)
    val sampleUsdQuote = Quote(spread = 1000.USD -> 1010.USD, lastPrice = 1005.USD)
    val sampleOpenOrders = OpenOrders(PeerPositions.empty(eurMarket))

    actor ! MarketInfoActor.Start(broker, registryActor)
    messageGateway.expectSubscriptionToBroker()
  }
}
