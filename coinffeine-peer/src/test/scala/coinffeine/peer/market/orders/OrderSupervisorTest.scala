package coinffeine.peer.market.orders

import scala.concurrent.duration._
import scala.util.Random

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit._
import org.joda.time.DateTime
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.ActivityLog
import coinffeine.model.currency._
import coinffeine.model.network.MutableCoinffeineNetworkProperties
import coinffeine.model.order._
import coinffeine.peer.CoinffeinePeerActor._
import coinffeine.peer.market.orders.OrderSupervisor.Delegates
import coinffeine.peer.market.orders.archive.OrderArchive
import coinffeine.peer.{CoinffeinePeerActor, ProtocolConstants}

class OrderSupervisorTest
  extends AkkaSpec(AkkaSpec.systemWithLoggingInterception("order-supervisor")) with Eventually {

  private val request1 = OrderRequest(Bid, 5.BTC, LimitPrice(500.EUR))
  private val request2 = OrderRequest(Ask, 2.BTC, LimitPrice(800.EUR))
  private val archivedOrder1 = ArchivedOrder(
    id = OrderId("order1"),
    orderType = request1.orderType,
    amount = request1.amount,
    price = request1.price,
    exchanges = Map.empty,
    log = ActivityLog.fromEvents(DateTime.now() -> OrderStatus.Completed)
  )

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
      givenNoArchivedOrders()
      startActor()
      shouldCreateOrder(request1)
    }

  it should "cancel an order when requested" in new Fixture {
    givenNoArchivedOrders()
    startActor()
    val order1 = shouldCreateOrder(request1)
    actor ! CancelOrder(order1.id)
    createdOrdersProbe.expectMsg(OrderActor.CancelOrder)
  }

  it should "remember created order actors" in new Fixture {
    givenNoArchivedOrders()
    startActor()
    val order1 = shouldCreateOrder(request1)
    val order2 = shouldCreateOrder(request2)
    restartActor()
    createdOrdersProbe.expectMsgAllOf(order1, order2)
  }

  it should "populate props with archived orders" in new Fixture {
    givenArchivedOrders(archivedOrder1)
    startActor()
    eventually(timeout = Timeout(3.seconds.dilated)) {
      networkProperties.orders.get(archivedOrder1.id) shouldBe Some(archivedOrder1)
    }
  }

  it should "not spawn order actors for archived orders" in new Fixture {
    givenNoArchivedOrders()
    startActor()
    val order1 = shouldCreateOrder(request1)
    givenArchivedOrders(order1)
    restartActor()
    createdOrdersProbe.expectNoMsg(100.millis.dilated)
  }

  it should "throw an exception if archived orders can't be retrieved" in new Fixture {
    givenArchiveLookupWillFail()
    EventFilter[Throwable](start = "Cannot retrieve archived orders", occurrences = 1) intercept {
      startActor()
    }
  }

  trait Fixture extends ProtocolConstants.DefaultComponent {
    val id = Random.nextInt().toHexString
    val createdOrdersProbe = TestProbe()
    val submissionProbe, archiveProbe = new MockSupervisedActor()
    val networkProperties = new MutableCoinffeineNetworkProperties
    private val delegates = new Delegates {
      def orderActorProps(order: ActiveOrder[_ <: FiatCurrency],
                          submission: ActorRef,
                          archive: ActorRef) =
        MockOrderActor.props(order, createdOrdersProbe)
      val submissionProps = submissionProbe.props()
      val archiveProps = archiveProbe.props()
    }
    private var archiveBehavior: PartialFunction[Any, Any] = _
    private val props = OrderSupervisor.props(id, delegates, networkProperties)
    var actor: ActorRef = _

    def givenNoArchivedOrders(): Unit = {
      givenArchivedOrders()
    }

    def givenArchivedOrders(orders: AnyCurrencyOrder*): Unit ={
      archiveBehavior = {
        case OrderArchive.Query() => OrderArchive.QueryResponse(orders)
      }
    }

    def givenArchiveLookupWillFail(): Unit = {
      archiveBehavior = {
        case OrderArchive.Query() => OrderArchive.QueryError()
      }
    }

    def startActor(): Unit = {
      actor = system.actorOf(props)
      watch(actor)
      submissionProbe.expectCreation()
      archiveProbe.expectCreation()
      archiveProbe.expectAskWithReply(archiveBehavior)
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
      archiveProbe.expectStop()
      expectTerminated(actor)
      startActor()
    }
  }
}
