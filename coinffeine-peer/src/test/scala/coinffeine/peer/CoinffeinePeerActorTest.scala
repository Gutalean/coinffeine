package coinffeine.peer

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe

import coinffeine.common.akka.ServiceActor
import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.currency.Currency.{Euro, UsDollar}
import coinffeine.model.currency.Implicits._
import coinffeine.model.event.{BitcoinConnectionStatus, CoinffeineConnectionStatus, EventChannelProbe}
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.peer.CoinffeinePeerActor._
import coinffeine.peer.bitcoin.BitcoinPeerActor
import coinffeine.peer.market.MarketInfoActor.{RequestOpenOrders, RequestQuote}
import coinffeine.peer.payment.PaymentProcessorActor.RetrieveBalance
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.messages.brokerage.{Market, OpenOrdersRequest, QuoteRequest}

class CoinffeinePeerActorTest extends AkkaSpec(ActorSystem("PeerActorTest")) {

  "A peer" must "report the connection status" in new StartedFixture {
    peer ! CoinffeinePeerActor.RetrieveConnectionStatus
    val bitcoinStatus = BitcoinConnectionStatus(0, BitcoinConnectionStatus.NotDownloading)
    bitcoinPeer.expectAskWithReply {
      case BitcoinPeerActor.RetrieveConnectionStatus => bitcoinStatus
    }
    val coinffeineStatus = CoinffeineConnectionStatus(10, Some(PeerId("broker")))
    gateway.expectAskWithReply {
      case MessageGateway.RetrieveConnectionStatus => coinffeineStatus
    }
    expectMsg(CoinffeinePeerActor.ConnectionStatus(bitcoinStatus, coinffeineStatus))
  }

  it must "delegate quote requests" in new StartedFixture {
    peer ! QuoteRequest(Market(Euro))
    marketInfo.expectForward(RequestQuote(Market(Euro)), self)
    peer ! OpenOrdersRequest(Market(UsDollar))
    marketInfo.expectForward(RequestOpenOrders(Market(UsDollar)), self)
  }

  it must "delegate order placement" in new StartedFixture {
    shouldForwardMessage(OpenOrder(Order(Bid, 10.BTC, Price(300.EUR))), orders)
  }

  it must "delegate retrieve open orders request" in new StartedFixture {
    shouldForwardMessage(RetrieveOpenOrders, orders)
  }

  it must "delegate order cancellation" in new StartedFixture {
    shouldForwardMessage(CancelOrder(OrderId.random(), "catastrophic failure"), orders)
  }

  it must "delegate fiat balance requests" in new StartedFixture {
    shouldForwardMessage(RetrieveBalance(UsDollar), paymentProcessor)
  }

  it must "delegate wallet balance requests" in new StartedFixture {
    peer ! RetrieveWalletBalance
    wallet.expectMsg(RetrieveWalletBalance)
    wallet.sender() should be (self)
  }

  it must "stop delegates on stop" in new StartedFixture {
    peer ! ServiceActor.Stop
    paymentProcessor.expectMsg(ServiceActor.Stop)
    bitcoinPeer.expectMsg(ServiceActor.Stop)
    gateway.expectMsg(ServiceActor.Stop)
  }

  trait Fixture {
    val localPort = 8080
    val brokerAddress = BrokerAddress("host", 8888)
    val brokerId = PeerId("broker")
    val wallet = TestProbe()
    val eventChannel = EventChannelProbe()

    val gateway, marketInfo, orders, bitcoinPeer, paymentProcessor = new MockSupervisedActor()
    val peer = system.actorOf(Props(new CoinffeinePeerActor(localPort, brokerAddress,
      PropsCatalogue(
        gateway = gateway.props,
        marketInfo = _ => marketInfo.props,
        orderSupervisor = _ => orders.props,
        paymentProcessor = paymentProcessor.props,
        bitcoinPeer = bitcoinPeer.props))))

    def shouldForwardMessage(message: Any, delegate: MockSupervisedActor): Unit = {
      peer ! message
      delegate.expectForward(message, self)
    }

    def shouldCreateActors(actors: MockSupervisedActor*): Unit = {
      actors.foreach(_.expectCreation())
    }

    def shouldRequestStart[Args](actor: MockSupervisedActor, args: Args): Unit = {
      actor.expectAskWithReply {
        case ServiceActor.Start(`args`) => ServiceActor.Started
      }
    }
  }

  trait StartedFixture extends Fixture {
    // Firstly, the actors are created before peer is started
    shouldCreateActors(gateway, paymentProcessor, bitcoinPeer, marketInfo)

    // Then we start the actor
    peer ! ServiceActor.Start({})

    // Then it must request the payment processor to start
    shouldRequestStart(paymentProcessor, {})

    // Then it must request the Bitcoin network to start
    shouldRequestStart(bitcoinPeer, {})

    // Then request to join to the Coinffeine network
    shouldRequestStart(gateway, MessageGateway.JoinAsPeer(`localPort`, `brokerAddress`))

    // Then request the wallet actor from bitcoin actor
    bitcoinPeer.expectAskWithReply {
      case BitcoinPeerActor.RetrieveWalletActor => BitcoinPeerActor.WalletActorRef(wallet.ref)
    }

    // Then request the order supervisor to initialize
    orders.expectCreation()

    // And finally indicate it succeed to start
    expectMsg(ServiceActor.Started)
  }
}
