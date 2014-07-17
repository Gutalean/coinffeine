package coinffeine.peer.market

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit.TestProbe

import coinffeine.common.test.MockActor.{MockReceived, MockStarted}
import coinffeine.common.test.{AkkaSpec, MockActor}
import coinffeine.model.currency.{FiatCurrency, FiatAmount}
import coinffeine.model.currency.Implicits._
import coinffeine.model.market.{Order, Ask, Bid, OrderBookEntry}
import coinffeine.model.network.PeerId
import coinffeine.peer.CoinffeinePeerActor._
import coinffeine.peer.ProtocolConstants

class OrderSupervisorTest extends AkkaSpec {

  class MockOrderActor(listener: ActorRef) extends Actor {
    private var order: Order[FiatCurrency] = _

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
    val peerId = PeerId("peer")
    val orderActorProbe = TestProbe()
    val submissionProbe = TestProbe()
    val eventChannel = TestProbe()
    val gateway = TestProbe()
    val paymentProcessor = TestProbe()
    val wallet = TestProbe()
    val actor = system.actorOf(Props(new OrderSupervisor(MockOrderActor.props(orderActorProbe),
      MockActor.props(submissionProbe), protocolConstants)))

    val order1 = Order(peerId, Bid, 5.BTC, 500.EUR)
    val order2 = Order(peerId, Ask, 2.BTC, 800.EUR)

    def givenOrderSupervisorIsInitialized(): Unit = {
      val brokerId = PeerId("Broker")
      val initMessage = OrderSupervisor.Initialize(
        brokerId, eventChannel.ref, gateway.ref, paymentProcessor.ref, wallet.ref)
      actor ! initMessage
      submissionProbe.expectMsgClass(classOf[MockStarted])
      val gatewayRef = gateway.ref
      submissionProbe.expectMsgPF() {
        case MockReceived(_, _, SubmissionSupervisor.Initialize(`brokerId`, `gatewayRef`)) =>
      }
    }

    def givenOpenOrder(order: Order[FiatCurrency]): Unit = {
      actor ! OpenOrder(order)
      orderActorProbe.expectMsgPF() {
        case OrderActor.Initialize(`order`, _, _, _, _, _) =>
      }
    }
  }
}
