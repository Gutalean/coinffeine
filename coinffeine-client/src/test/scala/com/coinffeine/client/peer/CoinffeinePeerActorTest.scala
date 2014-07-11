package com.coinffeine.client.peer

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe

import com.coinffeine.client.peer.CoinffeinePeerActor.{CancelOrder, OpenOrder, RetrieveOpenOrders}
import com.coinffeine.client.peer.MarketInfoActor.{RequestOpenOrders, RequestQuote}
import com.coinffeine.client.peer.orders.OrderSupervisor
import com.coinffeine.common._
import com.coinffeine.common.Currency.{Euro, UsDollar}
import com.coinffeine.common.Currency.Implicits._
import com.coinffeine.common.exchange.PeerId
import com.coinffeine.common.protocol.gateway.MessageGateway.{Bind, BindingError, BoundTo}
import com.coinffeine.common.protocol.messages.brokerage.{Market, OpenOrdersRequest, QuoteRequest}
import com.coinffeine.common.test.{AkkaSpec, MockActor}
import com.coinffeine.common.test.MockActor.{MockReceived, MockStarted}

class CoinffeinePeerActorTest extends AkkaSpec(ActorSystem("PeerActorTest")) {

  val ownId = PeerId("peerId")
  val address = PeerConnection("localhost", 8080)
  val brokerId = PeerId("broker")
  val brokerAddress = PeerConnection("host", 8888)
  val eventChannelProbe = TestProbe()
  val gatewayProbe = TestProbe()
  val marketInfoProbe = TestProbe()
  val ordersProbe = TestProbe()
  val peer = system.actorOf(Props(new CoinffeinePeerActor(ownId, address, brokerId, brokerAddress,
    MockActor.props(eventChannelProbe), MockActor.props(gatewayProbe),
    MockActor.props(marketInfoProbe), MockActor.props(ordersProbe))))
  var eventChannelRef: ActorRef = _
  var gatewayRef: ActorRef = _
  var ordersRef: ActorRef = _
  var marketInfoRef: ActorRef = _

  "A peer" must "start the message gateway" in {
    gatewayRef = gatewayProbe.expectMsgClass(classOf[MockStarted]).ref
  }

  it must "start the event channel actor" in {
    eventChannelRef = eventChannelProbe.expectMsgClass(classOf[MockStarted]).ref
  }

  it must "start the order submissions actor" in {
    ordersRef = ordersProbe.expectMsgClass(classOf[MockStarted]).ref
    val gw = gatewayRef
    val ec = eventChannelRef
    ordersProbe.expectMsgPF() {
      case MockReceived(_, _, OrderSupervisor.Initialize(_, `ec`, `gw`)) =>
    }
  }

  it must "make the message gateway start listening when connecting" in {
    gatewayProbe.expectNoMsg()
    peer ! CoinffeinePeerActor.Connect
    gatewayProbe.expectMsgPF() {
      case MockReceived(_, sender, Bind(_, `address`, _, _)) => sender ! BoundTo(address)
    }
    expectMsg(CoinffeinePeerActor.Connected)
  }

  it must "start the market info actor" in {
    marketInfoRef = marketInfoProbe.expectMsgClass(classOf[MockStarted]).ref
    val expectedInitialization = MarketInfoActor.Start(brokerId, gatewayRef)
    marketInfoProbe.expectMsgPF() {
     case MockReceived(_, _, `expectedInitialization`) =>
    }
  }

  it must "propagate failures when connecting" in {
    peer ! CoinffeinePeerActor.Connect
    val cause = new Exception("deep cause")
    gatewayProbe.expectMsgPF() {
      case MockReceived(_, sender, Bind(_, `address`, _, _)) => sender ! BindingError(cause)
    }
    expectMsg(CoinffeinePeerActor.ConnectionFailed(cause))
  }

  it must "delegate quote requests" in {
    peer ! QuoteRequest(Market(Euro))
    peer ! OpenOrdersRequest(Market(UsDollar))
    for (message <- Seq(RequestQuote(Market(Euro)), RequestOpenOrders(Market(UsDollar)))) {
      marketInfoProbe.expectMsgPF() {
        case MockReceived(_, _, `message`) =>
      }
    }
  }

  it must "delegate order placement" in {
    shouldDelegateMessage(OpenOrder(Order(null, Bid, 10.BTC, 300.EUR)), ordersProbe)
  }

  it must "delegate retrieve open orders request" in {
    shouldDelegateMessage(RetrieveOpenOrders, ordersProbe)
  }

  it must "delegate order cancellation" in {
    shouldDelegateMessage(CancelOrder(OrderId.random()), ordersProbe)
  }

  def shouldDelegateMessage(message: Any, delegate: TestProbe): Unit = {
    peer ! message
    val sender = self
    delegate.expectMsgPF() {
      case MockReceived(_, `sender`, `message`) =>
    }
  }

  it must "forward subscription commands to the event channel" in {
    val subscriber = TestProbe()
    val subscriberRef = subscriber.ref
    subscriber.send(peer, CoinffeinePeerActor.Subscribe)

    eventChannelProbe.expectMsgPF() {
      case MockReceived(_, `subscriberRef`, CoinffeinePeerActor.Subscribe) =>
    }
  }

  it must "forward unsubscription commands to the event channel" in {
    val subscriber = TestProbe()
    val subscriberRef = subscriber.ref
    subscriber.send(peer, CoinffeinePeerActor.Unsubscribe)

    eventChannelProbe.expectMsgPF() {
      case MockReceived(_, `subscriberRef`, CoinffeinePeerActor.Unsubscribe) =>
    }
  }
}
