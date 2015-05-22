package coinffeine.peer.market

import akka.actor.{Actor, ActorRef, Props}

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.Market
import coinffeine.model.network.BrokerId
import coinffeine.protocol.gateway.MessageForwarder
import coinffeine.protocol.gateway.MessageForwarder.RetrySettings
import coinffeine.protocol.gateway.MessageGateway.ForwardMessage
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.messages.brokerage._

/** Actor that subscribe for a market information on behalf of other actors.
  * It avoid unnecessary multiple concurrent requests and notify listeners.
  */
private class MarketInfoActor(gateway: ActorRef) extends Actor {
  import MarketInfoActor._

  private abstract class PendingRequest {
    private var listeners: Set[ActorRef] = Set.empty

    protected val request: PublicMessage
    protected val responseMatcher: PartialFunction[PublicMessage, Unit]
    private val forwarding = MessageForwarder.Factory(gateway, RetryPolicy)

    def addListener(listener: ActorRef): Unit = {
      listeners = listeners + listener
    }

    def notifyListeners(message: PublicMessage): Unit = {
      listeners.foreach(_ ! message)
    }

    def startForwarding(): Unit = {
      forwarding.forward(ForwardMessage(request, BrokerId)) {
        case message if responseMatcher.isDefinedAt(message) => message
      }
    }
  }

  private class PendingQuoteRequest(market: Market[_ <: FiatCurrency]) extends PendingRequest {
    override protected val request = QuoteRequest(market)
    override protected val responseMatcher: PartialFunction[PublicMessage, Unit] = {
      case Quote(`market`, _, _) =>
    }
  }

  private class PendingOpenOrdersRequest(market: Market[_ <: FiatCurrency]) extends PendingRequest {
    override protected val request = OpenOrdersRequest(market)
    override protected val responseMatcher: PartialFunction[PublicMessage, Unit] = {
      case OpenOrders(PeerPositions(`market`, _, _)) =>
    }
  }

  private var pendingRequests = Map.empty[InfoRequest, PendingRequest]

  override def receive: Receive = {
    case request @ RequestQuote(market) =>
      startOrAddToExistingRequest(request, sender())

    case request @ RequestOpenOrders(market) =>
      startOrAddToExistingRequest(request, sender())

    case quote @ Quote(_, _, _) =>
      completeRequest(RequestQuote(quote.market), quote)

    case openOrders @ OpenOrders(_) =>
      completeRequest(RequestOpenOrders(openOrders.orders.market), openOrders)
  }

  private def startOrAddToExistingRequest(request: InfoRequest, listener: ActorRef): Unit = {
    if (!pendingRequests.contains(request)) {
      startNewRequest(request)
    }
    pendingRequests(request).addListener(listener)
  }

  private def startNewRequest(request: InfoRequest): Unit = {
    val pendingRequest = request match {
      case RequestQuote(market) => new PendingQuoteRequest(market)
      case RequestOpenOrders(market) => new PendingOpenOrdersRequest(market)
    }
    pendingRequest.startForwarding()
    pendingRequests += request -> pendingRequest
  }

  private def completeRequest(request: InfoRequest, response: PublicMessage): Unit = {
    pendingRequests(request).notifyListeners(response)
    pendingRequests -= request
  }
}

object MarketInfoActor {

  val RetryPolicy = RetrySettings.Continuously

  def props(gateway: ActorRef): Props = Props(new MarketInfoActor(gateway))

  sealed trait InfoRequest

  /** The sender of this message will receive a [[Quote]] in response */
  case class RequestQuote(market: Market[_ <: FiatCurrency]) extends InfoRequest

  /** The sender of this message will receive an [[OpenOrders]] in response */
  case class RequestOpenOrders(market: Market[_ <: FiatCurrency]) extends InfoRequest
}
