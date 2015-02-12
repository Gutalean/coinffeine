package coinffeine.peer.market.orders

import scala.util.Random

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit.TestProbe

import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.peer.CoinffeinePeerActor._
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.market.orders.OrderSupervisor.Delegates

class OrderSupervisorTest extends AkkaSpec {

  class MockOrderActor(order: Order[_ <: FiatCurrency], listener: ActorRef) extends Actor {
    override def preStart(): Unit = {
      listener ! order
    }

    override def receive: Receive = {
      case other => listener ! other
    }
  }

  object MockOrderActor {
    def props(order: Order[_ <: FiatCurrency], probe: TestProbe) =
      Props(new MockOrderActor(order, probe.ref))
  }

  "An OrderSupervisor" should "create an OrderActor when receives a create order message" in
    new Fixture {
      shouldCreateActorForOrder(order1)
    }

  it should "cancel an order when requested" in new Fixture {
    shouldCreateActorForOrder(order1)
    val reason = "foo"
    actor ! CancelOrder(order1.id)
    createdOrdersProbe.expectMsg(OrderActor.CancelOrder)
  }

  it should "remember created order actors" in new Fixture {
    shouldCreateActorForOrder(order1)
    shouldCreateActorForOrder(order2)
    restartActor()
    createdOrdersProbe.expectMsgAllOf(order1, order2)
  }

  trait Fixture extends ProtocolConstants.DefaultComponent {
    val id = Random.nextInt().toHexString
    val order1 = Order.randomLimit(Bid, 5.BTC, Price(500.EUR))
    val order2 = Order.randomLimit(Ask, 2.BTC, Price(800.EUR))
    val createdOrdersProbe = TestProbe()
    val submissionProbe = new MockSupervisedActor()
    private val delegates = new Delegates {
      def orderActorProps(order: Order[_ <: FiatCurrency], submission: ActorRef) =
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

    def shouldCreateActorForOrder(order: Order[_ <: FiatCurrency]): Unit = {
      actor ! OpenOrder(order)
      createdOrdersProbe.expectMsg(order)
    }

    def restartActor(): Unit = {
      system.stop(actor)
      submissionProbe.expectStop()
      expectTerminated(actor)
      startActor()
    }
  }
}
