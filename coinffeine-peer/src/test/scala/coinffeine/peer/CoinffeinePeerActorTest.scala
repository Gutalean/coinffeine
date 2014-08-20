package coinffeine.peer

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe

import coinffeine.common.akka.ServiceActor
import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.currency.Currency.{Euro, UsDollar}
import coinffeine.model.currency.Implicits._
import coinffeine.model.event.{BitcoinConnectionStatus, CoinffeineConnectionStatus, EventChannelProbe}
import coinffeine.model.market.{Bid, Order, OrderId}
import coinffeine.model.network.PeerId
import coinffeine.peer.CoinffeinePeerActor._
import coinffeine.peer.bitcoin.BitcoinPeerActor
import coinffeine.peer.market.MarketInfoActor.{RequestOpenOrders, RequestQuote}
import coinffeine.peer.market.{MarketInfoActor, OrderSupervisor}
import coinffeine.peer.payment.PaymentProcessorActor.RetrieveBalance
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.messages.brokerage.{Market, OpenOrdersRequest, QuoteRequest}

class CoinffeinePeerActorTest extends AkkaSpec(ActorSystem("PeerActorTest")) {

  "A peer" must "start its children upon start" in new StartedFixture {
  }

  it must "report the connection status" in new StartedFixture {
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

  it must "fail to start on message gateway connect error" in new Fixture {
    peer ! ServiceActor.Start {}

    shouldCreateActors(gateway, paymentProcessor, bitcoinPeer, marketInfo, orders)
    shouldRequestStart(paymentProcessor, {})
    shouldRequestStart(bitcoinPeer, {})

    val cause = new Exception("deep cause")
    gateway.expectAskWithReply {
      case MessageGateway.Join(`localPort`, `brokerAddress`) =>
        MessageGateway.JoinError(cause)
    }
    expectMsgType[ServiceActor.StartFailure]
  }

  it must "delegate quote requests" in new StartedFixture {
    peer ! QuoteRequest(Market(Euro))
    marketInfo.expectForward(RequestQuote(Market(Euro)), self)
    peer ! OpenOrdersRequest(Market(UsDollar))
    marketInfo.expectForward(RequestOpenOrders(Market(UsDollar)), self)
  }

  it must "delegate order placement" in new StartedFixture {
    shouldForwardMessage(OpenOrder(Order(Bid, 10.BTC, 300.EUR)), orders)
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
        marketInfo = marketInfo.props,
        orderSupervisor = orders.props,
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
    shouldCreateActors(gateway, paymentProcessor, bitcoinPeer, marketInfo, orders)

    // Then we start the actor
    peer ! ServiceActor.Start({})

    // Then it must request the payment processor to start
    shouldRequestStart(paymentProcessor, {})

    // Then it must request the Bitcoin network to start
    shouldRequestStart(bitcoinPeer, {})

    // Then request to join to the Coinffeine network
    gateway.expectAskWithReply {
      case MessageGateway.Join(`localPort`, `brokerAddress`) =>
        MessageGateway.Joined(PeerId("client-peer"), brokerId)
    }

    // Then request the wallet actor from bitcoin actor
    bitcoinPeer.expectAskWithReply {
      case BitcoinPeerActor.RetrieveWalletActor => BitcoinPeerActor.WalletActorRef(wallet.ref)
    }

    // Then request the order supervisor to initialize
    val OrderSupervisor.Initialize(_, receivedPaymentProc, receivedBitcoinPeer, receivedWallet) =
      orders.expectMsgType[OrderSupervisor.Initialize]
    receivedPaymentProc should be (paymentProcessor.ref)
    receivedBitcoinPeer should be (bitcoinPeer.ref)
    receivedWallet should be (wallet.ref)

    // Then the market info is requested to start
    marketInfo.expectMsgPF { case MarketInfoActor.Start(_) => }

    // And finally indicate it succeed to start
    expectMsg(ServiceActor.Started)
  }
}
