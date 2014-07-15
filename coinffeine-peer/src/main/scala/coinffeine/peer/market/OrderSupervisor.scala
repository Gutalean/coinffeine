package coinffeine.peer.market

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor._
import akka.pattern._
import akka.util.Timeout

import coinffeine.model.currency.FiatAmount
import coinffeine.model.market.{OrderBookEntry, OrderId}
import coinffeine.model.network.PeerId
import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.CoinffeinePeerActor._
import coinffeine.peer.market.OrderActor.RetrieveStatus
import com.coinffeine.common.ProtocolConstants

/** Manages orders */
class OrderSupervisor(orderActorProps: Props,
                      submissionSupervisorProps: Props,
                      protocolConstants: ProtocolConstants) extends Actor with ActorLogging {

  override def receive: Receive = {
    case init: OrderSupervisor.Initialize =>
      new InitializedOrdersActor(init).start()
  }

  private class InitializedOrdersActor(init: OrderSupervisor.Initialize) {
    import init._

    private val submission = context.actorOf(submissionSupervisorProps)
    private var orders = Map.empty[OrderId, ActorRef]

    def start(): Unit = {
      submission ! SubmissionSupervisor.Initialize(brokerId, gateway)
      context.become(waitingForOrders)
    }

    private val waitingForOrders: Receive = {

      case OpenOrder(order) =>
        val ref = context.actorOf(orderActorProps, s"order-${order.id.value}")
        ref ! OrderActor.Initialize(order, submission, eventChannel, gateway)
        orders += order.id -> ref

      case CancelOrder(orderId) =>
        orders.get(orderId).foreach(_ ! OrderActor.CancelOrder)
        orders = orders.filterNot(_._1 == orderId)

      case RetrieveOpenOrders =>
        import context.dispatcher
        implicit val timeout = Timeout(1.second)
        Future.sequence(orders.values.toSeq.map { ref =>
          (ref ? RetrieveStatus).mapTo[OrderBookEntry[FiatAmount]]
        }).map(RetrievedOpenOrders.apply).pipeTo(sender())
    }
  }
}

object OrderSupervisor {

  case class Initialize(brokerId: PeerId,
                        eventChannel: ActorRef,
                        gateway: ActorRef)

  trait Component { this: SubmissionSupervisor.Component
    with ProtocolConstants.Component
    with OrderActor.Component =>
    lazy val orderSupervisorProps = Props(
      new OrderSupervisor(orderActorProps, submissionSupervisorProps, protocolConstants))
  }
}
