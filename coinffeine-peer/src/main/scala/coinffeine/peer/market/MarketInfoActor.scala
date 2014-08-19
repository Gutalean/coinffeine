package coinffeine.peer.market

import akka.actor.{Actor, ActorRef, Props}

import coinffeine.common.akka.ServiceRegistry
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, ReceiveMessage, SubscribeToBroker}
import coinffeine.protocol.messages.brokerage._

/** Actor that subscribe for a market information on behalf of other actors.
  * It avoid unnecessary multiple concurrent requests and notify listeners.
  */
class MarketInfoActor extends Actor {

  import coinffeine.peer.market.MarketInfoActor._

  override def receive: Receive = {
    case init: Start => new InitializedActor(init).start()
  }

  private class InitializedActor(init: Start) {
    import context.dispatcher
    import init._

    private val gateway = new ServiceRegistry(registry).eventuallyLocate(MessageGateway.ServiceId)
    private var pendingRequests = Map.empty[InfoRequest, Set[ActorRef]].withDefaultValue(Set.empty)

    def start(): Unit = {
      subscribeToMessages()
      context.become(initializedReceive)
    }

    private def subscribeToMessages(): Unit = {
      gateway ! SubscribeToBroker {
        case Quote(_, _, _) | OpenOrders(_) =>
      }
    }

    private val initializedReceive: Receive = {
      case request @ RequestQuote(market) =>
        startRequest(request, sender()) {
          gateway ! ForwardMessage(QuoteRequest(market), broker)
        }

      case request @ RequestOpenOrders(market) =>
        startRequest(request, sender()) {
          gateway ! ForwardMessage(OpenOrdersRequest(market), broker)
        }

      case ReceiveMessage(quote: Quote[FiatCurrency], _) =>
        completeRequest(RequestQuote(quote.market), quote)

      case ReceiveMessage(openOrders: OpenOrders[_], _) =>
        completeRequest(RequestOpenOrders(openOrders.orders.market), openOrders)
    }

    private def startRequest(request: InfoRequest, listener: ActorRef)(block: => Unit): Unit = {
      if (pendingRequests(request).isEmpty) {
        block
      }
      pendingRequests += request -> (pendingRequests(request) + listener)
    }

    private def completeRequest(request: InfoRequest, response: Any): Unit = {
      pendingRequests(request).foreach(_ ! response)
      pendingRequests += request -> Set.empty
    }
  }
}

object MarketInfoActor {

  val props: Props = Props(new MarketInfoActor)

  /** Initialize the actor to subscribe for market information */
  case class Start(broker: PeerId, registry: ActorRef)

  sealed trait InfoRequest

  /** The sender of this message will receive a [[Quote]] in response */
  case class RequestQuote(market: Market[FiatCurrency]) extends InfoRequest

  /** The sender of this message will receive an [[OpenOrders]] in response */
  case class RequestOpenOrders(market: Market[FiatCurrency]) extends InfoRequest
}
