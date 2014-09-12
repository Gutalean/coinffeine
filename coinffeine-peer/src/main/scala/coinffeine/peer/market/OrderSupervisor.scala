package coinffeine.peer.market

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor._
import akka.pattern._
import akka.util.Timeout

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{Order, OrderId}
import coinffeine.peer.CoinffeinePeerActor._
import coinffeine.peer.market.OrderActor.RetrieveStatus

/** Manages orders */
private class OrderSupervisor(orderActorProps: OrderSupervisor.OrderActorProps,
                              submissionSupervisorProps: OrderSupervisor.SubmissionSupervisorProps)
  extends Actor with ActorLogging {

  override def receive: Receive = {
    case init: OrderSupervisor.Initialize =>
      new InitializedOrdersActor(init).start()
  }

  private class InitializedOrdersActor(init: OrderSupervisor.Initialize) {
    import init._

    private val submission = context.actorOf(submissionSupervisorProps(gateway))
    private var orders = Map.empty[OrderId, ActorRef]

    def start(): Unit = {
      context.become(waitingForOrders)
    }

    private val waitingForOrders: Receive = {

      case OpenOrder(order) =>
        val ref = context.actorOf(orderActorProps(order, submission), s"order-${order.id.value}")
        orders += order.id -> ref

      case CancelOrder(orderId, reason) =>
        orders.get(orderId).foreach(_ ! OrderActor.CancelOrder(reason))
        orders = orders.filterNot(_._1 == orderId)

      case RetrieveOpenOrders =>
        import context.dispatcher
        implicit val timeout = Timeout(1.second)
        Future.sequence(orders.values.toSeq.map { ref =>
          (ref ? RetrieveStatus).mapTo[Order[FiatCurrency]]
        }).map(RetrievedOpenOrders.apply).pipeTo(sender())
    }
  }
}

object OrderSupervisor {

  /** Props of a submission supervisor given a message gateway */
  type SubmissionSupervisorProps = ActorRef => Props

  type OrderActorProps = (Order[_ <: FiatCurrency], ActorRef) => Props

  case class Collaborators(gateway: ActorRef,
                           paymentProcessor: ActorRef,
                           bitcoinPeer: ActorRef,
                           wallet: ActorRef)

  case class Initialize(gateway: ActorRef,
                        paymentProcessor: ActorRef,
                        bitcoinPeer: ActorRef,
                        wallet: ActorRef)

  def props(orderActorProps: OrderActorProps, submissionSupervisorProps: SubmissionSupervisorProps) =
    Props(new OrderSupervisor(orderActorProps, submissionSupervisorProps))
}
