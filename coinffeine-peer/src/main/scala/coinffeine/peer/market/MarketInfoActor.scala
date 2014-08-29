package coinffeine.peer.market

import akka.actor.{Actor, ActorRef, Props}

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.network.BrokerId
import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, ReceiveMessage, Subscribe}
import coinffeine.protocol.messages.brokerage._

/** Actor that subscribe for a market information on behalf of other actors.
  * It avoid unnecessary multiple concurrent requests and notify listeners.
  */
private class MarketInfoActor(gateway: ActorRef) extends Actor {
  import coinffeine.peer.market.MarketInfoActor._

  override def preStart(): Unit = {
    subscribeToMessages()
  }

  private var pendingRequests = Map.empty[InfoRequest, Set[ActorRef]].withDefaultValue(Set.empty)

  override def receive: Receive = {
    case request @ RequestQuote(market) =>
      startRequest(request, sender()) {
        gateway ! ForwardMessage(QuoteRequest(market), BrokerId)
      }

    case request @ RequestOpenOrders(market) =>
      startRequest(request, sender()) {
        gateway ! ForwardMessage(OpenOrdersRequest(market), BrokerId)
      }

    case ReceiveMessage(quote @ Quote(_, _, _), _) =>
      completeRequest(RequestQuote(quote.market), quote)

    case ReceiveMessage(openOrders: OpenOrders[_], _) =>
      completeRequest(RequestOpenOrders(openOrders.orders.market), openOrders)
  }

  private def subscribeToMessages(): Unit = {
    gateway ! Subscribe.fromBroker {
      case Quote(_, _, _) | OpenOrders(_) =>
    }
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

object MarketInfoActor {

  def props(gateway: ActorRef): Props = Props(new MarketInfoActor(gateway))

  sealed trait InfoRequest

  /** The sender of this message will receive a [[Quote]] in response */
  case class RequestQuote(market: Market[FiatCurrency]) extends InfoRequest

  /** The sender of this message will receive an [[OpenOrders]] in response */
  case class RequestOpenOrders(market: Market[FiatCurrency]) extends InfoRequest
}
