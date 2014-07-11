package com.coinffeine.client.peer.orders

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit.TestProbe

import com.coinffeine.client.peer.CoinffeinePeerActor.{RetrieveOpenOrders, CancelOrder, OpenOrder, RetrievedOpenOrders}
import com.coinffeine.common._
import com.coinffeine.common.Currency.Implicits._
import com.coinffeine.common.exchange.PeerId
import com.coinffeine.common.protocol.ProtocolConstants
import com.coinffeine.common.test.{AkkaSpec, MockActor}
import com.coinffeine.common.test.MockActor.MockStarted

class OrderSupervisorTest extends AkkaSpec {

  class MockOrderActor(listener: ActorRef) extends Actor {
    private var order: Order[FiatAmount] = _

    override def receive: Receive = {
      case init: OrderActor.Initialize =>
        order = init.order
        listener ! init
      case OrderActor.RetrieveStatus =>
        sender() ! order
      case other =>
        listener ! other
    }
  }

  object MockOrderActor {
    def props(probe: TestProbe) = Props(new MockOrderActor(probe.ref))
  }

  "An OrderSupervisor" should "initialize the OrderSubmissionSupervisor" in new Fixture {
    givenOrderSupervisorIsInitialized()
  }

  it should "create an OrderActor when receives a create order message" in new Fixture {
    givenOrderSupervisorIsInitialized()
    givenOpenOrder(order1)
  }

  it should "cancel an order when requested" in new Fixture {
    givenOrderSupervisorIsInitialized()
    givenOpenOrder(order1)
    actor ! CancelOrder(order1.id)
    orderActorProbe.expectMsg(OrderActor.CancelOrder)
  }

  it should "collect all orders in a RetrievedOpenOrders message" in new Fixture {
    givenOrderSupervisorIsInitialized()
    givenOpenOrder(order1)
    givenOpenOrder(order2)
    actor ! RetrieveOpenOrders
    expectMsg(RetrievedOpenOrders(Seq(order1, order2)))
  }

  trait Fixture extends ProtocolConstants.DefaultComponent {
    val orderActorProbe = TestProbe()
    val submissionProbe = TestProbe()
    val eventChannel = TestProbe()
    val gateway = TestProbe()
    val actor = system.actorOf(Props(new OrderSupervisor(MockOrderActor.props(orderActorProbe),
      MockActor.props(submissionProbe), protocolConstants)))

    val order1 = Order(Bid, 5.BTC, 500.EUR)
    val order2 = Order(Ask, 2.BTC, 800.EUR)
    
    def givenOrderSupervisorIsInitialized(): Unit = {
      val initMessage = OrderSupervisor.Initialize(
        PeerId("Peer_1"), PeerId("Broker"), eventChannel.ref, gateway.ref)
      actor ! initMessage
      submissionProbe.expectMsgClass(classOf[MockStarted])
    }
   
    def givenOpenOrder(order: Order[FiatAmount]): Unit = {
      actor ! OpenOrder(order)
      orderActorProbe.expectMsgPF() {
        case OrderActor.Initialize(`order`, _) =>
      }
    }
  }
}
