package com.coinffeine.client.peer

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe

import com.coinffeine.client.peer.CoinffeinePeerActor.{CancelOrder, OpenOrder, RetrieveOpenOrders}
import com.coinffeine.client.peer.MarketInfoActor.{RequestOpenOrders, RequestQuote}
import com.coinffeine.client.peer.orders.OrdersActor
import com.coinffeine.common._
import com.coinffeine.common.Currency.{Euro, UsDollar}
import com.coinffeine.common.Currency.Implicits._
import com.coinffeine.common.protocol.gateway.MessageGateway.{Bind, BindingError, BoundTo}
import com.coinffeine.common.protocol.messages.brokerage.{Market, OpenOrdersRequest, QuoteRequest}
import com.coinffeine.common.test.{AkkaSpec, MockActor}
import com.coinffeine.common.test.MockActor.{MockReceived, MockStarted}

class CoinffeinePeerActorTest extends AkkaSpec(ActorSystem("PeerActorTest")) {

  val address = PeerConnection("localhost", 8080)
  val brokerAddress = PeerConnection("host", 8888)
  val gatewayProbe = TestProbe()
  val marketInfoProbe = TestProbe()
  val ordersProbe = TestProbe()
  val peer = system.actorOf(Props(new CoinffeinePeerActor(address, brokerAddress,
    MockActor.props(gatewayProbe), MockActor.props(marketInfoProbe),
    MockActor.props(ordersProbe))))
  var gatewayRef: ActorRef = _
  var ordersRef: ActorRef = _
  var marketInfoRef: ActorRef = _

  "A peer" must "start the message gateway" in {
    gatewayRef = gatewayProbe.expectMsgClass(classOf[MockStarted]).ref
  }

  it must "start the order submissions actor" in {
    ordersRef = ordersProbe.expectMsgClass(classOf[MockStarted]).ref
    val gw = gatewayRef
    ordersProbe.expectMsgPF() {
      case MockReceived(_, _, OrdersActor.Initialize(_, _, `gw`)) =>
    }
  }

  it must "make the message gateway start listening when connecting" in {
    gatewayProbe.expectNoMsg()
    peer ! CoinffeinePeerActor.Connect
    gatewayProbe.expectMsgPF() {
      case MockReceived(_, sender, Bind(`address`)) => sender ! BoundTo(address)
    }
    expectMsg(CoinffeinePeerActor.Connected)
  }

  it must "start the market info actor" in {
    marketInfoRef = marketInfoProbe.expectMsgClass(classOf[MockStarted]).ref
    val expectedInitialization = MarketInfoActor.Start(brokerAddress, gatewayRef)
    marketInfoProbe.expectMsgPF() {
     case MockReceived(_, _, `expectedInitialization`) =>
    }
  }

  it must "propagate failures when connecting" in {
    peer ! CoinffeinePeerActor.Connect
    val cause = new Exception("deep cause")
    gatewayProbe.expectMsgPF() {
      case MockReceived(_, sender, Bind(`address`)) => sender ! BindingError(cause)
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
    shouldDelegateMessage(OpenOrder(Order(Bid, 10.BTC, 300.EUR)), ordersProbe)
  }

  it must "delegate retrieve open orders request" in {
    shouldDelegateMessage(RetrieveOpenOrders, ordersProbe)
  }

  it must "delegate order cancellation" in {
    shouldDelegateMessage(CancelOrder(Order(Bid, 10.BTC, 300.EUR)), ordersProbe)
  }

  def shouldDelegateMessage(message: Any, delegate: TestProbe): Unit = {
    peer ! message
    val sender = self
    delegate.expectMsgPF() {
      case MockReceived(_, `sender`, `message`) =>
    }
  }
}
