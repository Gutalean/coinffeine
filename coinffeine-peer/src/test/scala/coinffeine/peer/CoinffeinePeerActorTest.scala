package coinffeine.peer

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe

import coinffeine.common.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.currency.Currency.{Euro, UsDollar}
import coinffeine.model.currency.Implicits._
import coinffeine.model.market.{Bid, OrderBookEntry, OrderId}
import coinffeine.model.network.PeerId
import coinffeine.peer.CoinffeinePeerActor._
import coinffeine.peer.market.MarketInfoActor.{RequestOpenOrders, RequestQuote}
import coinffeine.peer.market.{MarketInfoActor, OrderSupervisor}
import coinffeine.protocol.gateway.MessageGateway.{Bind, BindingError, BoundTo}
import coinffeine.protocol.gateway.PeerConnection
import coinffeine.protocol.messages.brokerage.{Market, OpenOrdersRequest, QuoteRequest}

class CoinffeinePeerActorTest extends AkkaSpec(ActorSystem("PeerActorTest")) {

  val ownId = PeerId("peerId")
  val address = PeerConnection("localhost", 8080)
  val brokerId = PeerId("broker")
  val brokerAddress = PeerConnection("host", 8888)
  val eventChannel = new MockSupervisedActor()
  val gateway = new MockSupervisedActor()
  val marketInfo = new MockSupervisedActor()
  val orders = new MockSupervisedActor()
  val wallet = new MockSupervisedActor()
  val paymentProcessor = new MockSupervisedActor()
  val peer = system.actorOf(Props(new CoinffeinePeerActor(
    ownId, address, brokerId, brokerAddress, PropsCatalogue(
      eventChannel = eventChannel.props,
      gateway = gateway.props,
      marketInfo = marketInfo.props,
      orderSupervisor = orders.props,
      wallet = wallet.props,
      paymentProcessor = paymentProcessor.props))))

  "A peer" must "start the message gateway" in {
    gateway.expectCreation()
  }

  it must "start the event channel actor" in {
    eventChannel.expectCreation()
  }


  it must "start the wallet actor" in {
    wallet.expectCreation()
  }

  it must "start the payment processor actor" in {
    paymentProcessor.expectCreation()
  }

  it must "start the order submissions actor" in {
    orders.expectCreation()
    orders.expectMsg(OrderSupervisor.Initialize(
      brokerId, eventChannel.ref, gateway.ref, paymentProcessor.ref, wallet.ref))
  }

  it must "make the message gateway start listening when connecting" in {
    gateway.probe.expectNoMsg()
    peer ! CoinffeinePeerActor.Connect
    gateway.expectAskWithReply {
      case Bind(_, `address`, _, _) => BoundTo(address)
    }
    expectMsg(CoinffeinePeerActor.Connected)
  }

  it must "start the market info actor" in {
    marketInfo.expectCreation()
    marketInfo.expectMsg(MarketInfoActor.Start(brokerId, gateway.ref))
  }

  it must "propagate failures when connecting" in {
    peer ! CoinffeinePeerActor.Connect
    val cause = new Exception("deep cause")
    gateway.expectAskWithReply {
      case Bind(_, `address`, _, _) => BindingError(cause)
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
    shouldForwardMessage(OpenOrder(OrderBookEntry(Bid, 10.BTC, 300.EUR)), orders)
  }

  it must "delegate retrieve open orders request" in {
    shouldForwardMessage(RetrieveOpenOrders, orders)
  }

  it must "delegate order cancellation" in {
    shouldForwardMessage(CancelOrder(OrderId.random()), orders)
  }

  it must "forward subscription commands to the event channel" in {
    val subscriber = TestProbe()
    subscriber.send(peer, CoinffeinePeerActor.Subscribe)
    eventChannel.expectForward(CoinffeinePeerActor.Subscribe, subscriber.ref)
  }

  it must "forward unsubscription commands to the event channel" in {
    val subscriber = TestProbe()
    subscriber.send(peer, CoinffeinePeerActor.Unsubscribe)
    eventChannel.expectForward(CoinffeinePeerActor.Unsubscribe, subscriber.ref)
  }

  it must "delegate wallet balance requests" in {
    shouldForwardMessage(RetrieveWalletBalance, wallet)
  }

  def shouldForwardMessage(message: Any, delegate: MockSupervisedActor): Unit = {
    peer ! message
    delegate.expectForward(message, self)
  }
}
