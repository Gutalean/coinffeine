package coinffeine.peer.market

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import com.google.bitcoin.core.NetworkParameters
import com.typesafe.config.Config

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{Order, OrderId}
import coinffeine.peer.CoinffeinePeerActor._
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.amounts.ExchangeAmountsCalculator
import coinffeine.peer.market.OrderActor.RetrieveStatus

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
      submission ! SubmissionSupervisor.Initialize(registry)
      context.become(waitingForOrders)
    }

    private val waitingForOrders: Receive = {

      case OpenOrder(order) =>
        val ref = context.actorOf(orderActorProps, s"order-${order.id.value}")

        ref ! OrderActor.Initialize(order, submission, registry, paymentProcessor,
          bitcoinPeer, wallet)
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

  case class Initialize(registry: ActorRef,
                        paymentProcessor: ActorRef,
                        bitcoinPeer: ActorRef,
                        wallet: ActorRef)

  def props(exchangeActorProps: Props,
            config: Config,
            network: NetworkParameters,
            constants: ProtocolConstants,
            amountsCalculator: ExchangeAmountsCalculator) = Props(new OrderSupervisor(
      OrderActor.props(exchangeActorProps, config, network, amountsCalculator),
      SubmissionSupervisor.props(constants),
      constants
    ))
}
