package coinffeine.peer.market

import scala.concurrent.duration._

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout

import coinffeine.model.currency.{FiatAmount, FiatCurrency}
import coinffeine.model.market.{OrderBookEntry, OrderId}
import coinffeine.model.network.PeerId
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.market.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import coinffeine.protocol.messages.brokerage.Market

class SubmissionSupervisor(protocolConstants: ProtocolConstants) extends Actor with ActorLogging{
  private implicit val timeout = Timeout(1.second)

  override def receive: Receive = {
    case init: SubmissionSupervisor.Initialize =>
      new InitializedSubmissionSupervisor(init).start()
  }

  private class InitializedSubmissionSupervisor(init: SubmissionSupervisor.Initialize) {
    import init._

    private var delegatesByMarket = Map.empty[Market[FiatCurrency], ActorRef]

    def start(): Unit = {
      context.become(waitingForOrders)
    }

    private val waitingForOrders: Receive = {

      case message @ KeepSubmitting(order) =>
        getOrCreateDelegate(marketOf(order)) forward message

      case message @ StopSubmitting(order) =>
      delegatesByMarket.values.foreach(_ forward message)
    }

    private def marketOf(order: OrderBookEntry[FiatAmount]) = Market(currency = order.price.currency)

    private def getOrCreateDelegate(market: Market[FiatCurrency]): ActorRef =
    delegatesByMarket.getOrElse(market, createDelegate(market))

    private def createDelegate(market: Market[FiatCurrency]): ActorRef = {
      log.info(s"Start submitting to $market")
      val newDelegate = context.actorOf(MarketSubmissionActor.props(protocolConstants))
      newDelegate ! MarketSubmissionActor.Initialize(market, registry, brokerId)
      delegatesByMarket += market -> newDelegate
      newDelegate
    }
  }
}

object SubmissionSupervisor {

  case class Initialize(brokerId: PeerId, registry: ActorRef)

  case class KeepSubmitting(order: OrderBookEntry[FiatAmount])

  case class StopSubmitting(orderId: OrderId)

  case class InMarket(order: OrderBookEntry[FiatAmount])

  case class Offline(order: OrderBookEntry[FiatAmount])

  def props(constants: ProtocolConstants) = Props(new SubmissionSupervisor(constants))
}
