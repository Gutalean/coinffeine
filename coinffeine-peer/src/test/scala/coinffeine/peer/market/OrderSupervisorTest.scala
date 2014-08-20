package coinffeine.peer.market

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit.TestProbe

import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.currency.Implicits._
import coinffeine.model.market.{Ask, Bid, Order}
import coinffeine.model.network.PeerId
import coinffeine.peer.CoinffeinePeerActor._
import coinffeine.peer.ProtocolConstants

class OrderSupervisorTest extends AkkaSpec {

  class MockOrderActor(listener: ActorRef) extends Actor {
    private var order: Order[FiatCurrency] = _

    override def receive: Receive = {
      case init @ OrderActor.Initialize(_, _, _, _, _, _) =>
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
    val reason = "foo"
    actor ! CancelOrder(order1.id, reason)
    orderActorProbe.expectMsg(OrderActor.CancelOrder(reason))
  }

  it should "collect all orders in a RetrievedOpenOrders message" in new Fixture {
    givenOrderSupervisorIsInitialized()
    givenOpenOrder(order1)
    givenOpenOrder(order2)
    actor ! RetrieveOpenOrders
    expectMsg(RetrievedOpenOrders(Seq(order1, order2)))
  }

  trait Fixture extends ProtocolConstants.DefaultComponent {
    val orderActorProbe, eventChannel, gateway, paymentProcessor, bitcoinPeer, wallet = TestProbe()
    val submissionProbe = new MockSupervisedActor()
    val actor = system.actorOf(Props(new OrderSupervisor(MockOrderActor.props(orderActorProbe),
      submissionProbe.props, protocolConstants)))

    val order1 = Order(Bid, 5.BTC, 500.EUR)
    val order2 = Order(Ask, 2.BTC, 800.EUR)

    def givenOrderSupervisorIsInitialized(): Unit = {
      val brokerId = PeerId("Broker")
      val initMessage = OrderSupervisor.Initialize(
        brokerId, gateway.ref, paymentProcessor.ref, bitcoinPeer.ref, wallet.ref)
      actor ! initMessage
      submissionProbe.expectCreation()
      val gatewayRef = gateway.ref
      submissionProbe.expectMsg(SubmissionSupervisor.Initialize(gatewayRef))
    }

    def givenOpenOrder(order: Order[FiatCurrency]): Unit = {
      actor ! OpenOrder(order)
      orderActorProbe.expectMsgPF() {
        case init: OrderActor.Initialize[_] if init.order == order =>
      }
    }
  }
}
