package coinffeine.peer

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe

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

  "A peer" must "start the message gateway" in {
    gateway.expectCreation()
  }

  it must "start the bitcoin peer and retrieve the wallet actor" in {
    bitcoinPeer.expectCreation()
    bitcoinPeer.expectAskWithReply {
      case BitcoinPeerActor.RetrieveWalletActor => BitcoinPeerActor.WalletActorRef(wallet.ref)
    }
  }

  it must "start the payment processor actor" in {
    paymentProcessor.expectCreation()
  }

  it must "connect to both networks" in {
    gateway.probe.expectNoMsg()
    peer ! CoinffeinePeerActor.Connect
    bitcoinPeer.expectMsg(BitcoinPeerActor.JoinBitcoinNetwork)
    gateway.expectAskWithReply {
      case MessageGateway.Join(`localPort`, `brokerAddress`) =>
        MessageGateway.Joined(PeerId("peer id"), brokerId)
    }
    expectMsg(CoinffeinePeerActor.Connected)
  }

  it must "report the connection status" in {
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

  it must "start the order supervisor actor" in {
    orders.expectCreation()
    val OrderSupervisor.Initialize(_, receivedPaymentProc, receivedBitcoinPeer, _) =
      orders.expectMsgType[OrderSupervisor.Initialize]
    receivedPaymentProc should be (paymentProcessor.ref)
    receivedBitcoinPeer should be (bitcoinPeer.ref)
  }

  it must "start the market info actor" in {
    marketInfo.expectCreation()
    marketInfo.expectMsgType[MarketInfoActor.Start]
  }

  it must "propagate failures when connecting" in {
    val dummyHelper = new MockSupervisedActor()
    val localGateway = new MockSupervisedActor()
    val uninitializedPeer = system.actorOf(Props(new CoinffeinePeerActor(localPort, brokerAddress,
      PropsCatalogue(
        gateway = localGateway.props,
        marketInfo = dummyHelper.props,
        orderSupervisor = dummyHelper.props,
        paymentProcessor = dummyHelper.props,
        bitcoinPeer = dummyHelper.props))))
    localGateway.expectCreation()
    uninitializedPeer ! CoinffeinePeerActor.Connect
    val cause = new Exception("deep cause")
    localGateway.expectAskWithReply {
      case MessageGateway.Join(`localPort`, `brokerAddress`) => JoinError(cause)
    }
    expectMsg(CoinffeinePeerActor.ConnectionFailed(cause))
  }

  it must "delegate quote requests" in {
    peer ! QuoteRequest(Market(Euro))
    marketInfo.expectForward(RequestQuote(Market(Euro)), self)
    peer ! OpenOrdersRequest(Market(UsDollar))
    marketInfo.expectForward(RequestOpenOrders(Market(UsDollar)), self)
  }

  it must "delegate order placement" in {
    shouldForwardMessage(OpenOrder(Order(Bid, 10.BTC, 300.EUR)), orders)
  }

  it must "delegate retrieve open orders request" in {
    shouldForwardMessage(RetrieveOpenOrders, orders)
  }

  it must "delegate order cancellation" in {
    shouldForwardMessage(CancelOrder(OrderId.random(), "catastrophic failure"), orders)
  }

  it must "delegate fiat balance requests" in {
    shouldForwardMessage(RetrieveBalance(UsDollar), paymentProcessor)
  }

  it must "delegate wallet balance requests" in {
    peer ! RetrieveWalletBalance
    wallet.expectMsg(RetrieveWalletBalance)
    wallet.sender() should be (self)
  }

  private def shouldForwardMessage(message: Any, delegate: MockSupervisedActor): Unit = {
    peer ! message
    delegate.expectForward(message, self)
  }
}
