package coinffeine.peer.market.orders

import scala.util.Random

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit.TestProbe

import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.currency._
import coinffeine.model.order._
import coinffeine.peer.CoinffeinePeerActor._
import coinffeine.peer.market.orders.OrderSupervisor.Delegates
import coinffeine.peer.{CoinffeinePeerActor, ProtocolConstants}

class OrderSupervisorTest extends AkkaSpec {

  val request1 = OrderRequest(Bid, 5.BTC, LimitPrice(500.EUR))
  val request2 = OrderRequest(Ask, 2.BTC, LimitPrice(800.EUR))

  class MockOrderActor(order: ActiveOrder[_ <: FiatCurrency], listener: ActorRef) extends Actor {
    override def preStart(): Unit = {
      listener ! order
    }

    override def receive: Receive = {
      case other => listener ! other
    }
  }

  object MockOrderActor {
    def props(order: ActiveOrder[_ <: FiatCurrency], probe: TestProbe) =
      Props(new MockOrderActor(order, probe.ref))
  }

  "An OrderSupervisor" should "create an OrderActor when receives a create order message" in
    new Fixture {
      shouldCreateOrder(request1)
    }

  it should "cancel an order when requested" in new Fixture {
    val order1 = shouldCreateOrder(request1)
    actor ! CancelOrder(order1.id)
    createdOrdersProbe.expectMsg(OrderActor.CancelOrder)
  }

  it should "remember created order actors" in new Fixture {
    val order1 = shouldCreateOrder(request1)
    val order2 = shouldCreateOrder(request2)
    restartActor()
    createdOrdersProbe.expectMsgAllOf(order1, order2)
  }

  trait Fixture extends ProtocolConstants.DefaultComponent {
    val id = Random.nextInt().toHexString
    val createdOrdersProbe = TestProbe()
    val submissionProbe = new MockSupervisedActor()
    private val delegates = new Delegates {
      def orderActorProps(order: ActiveOrder[_ <: FiatCurrency], submission: ActorRef) =
        MockOrderActor.props(order, createdOrdersProbe)
      val submissionProps = submissionProbe.props()
    }
    private val props = OrderSupervisor.props(id, delegates)
    var actor: ActorRef = _
    startActor()

    def startActor(): Unit = {
      actor = system.actorOf(props)
      watch(actor)
      submissionProbe.expectCreation()
    }

    def shouldCreateOrder(request: OrderRequest[_ <: FiatCurrency]): ActiveOrder[_ <: FiatCurrency] = {
      actor ! OpenOrder(request)
      createdOrdersProbe.expectMsgType[ActiveOrder[_ <: FiatCurrency]]
      val order = expectMsgType[CoinffeinePeerActor.OrderOpened].order
      order.orderType shouldBe request.orderType
      order.amount shouldBe request.amount
      order.price shouldBe request.price
      order
    }

    def restartActor(): Unit = {
      system.stop(actor)
      submissionProbe.expectStop()
      expectTerminated(actor)
      startActor()
    }
  }
}
