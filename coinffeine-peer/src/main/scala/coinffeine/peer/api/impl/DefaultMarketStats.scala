package coinffeine.peer.api.impl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.util.Timeout

import coinffeine.common.akka.AskPattern
import coinffeine.model.market.Market
import coinffeine.peer.api.MarketStats
import coinffeine.protocol.messages.brokerage._

private[impl] class DefaultMarketStats(peer: ActorRef)
  extends MarketStats with PeerActorWrapper {

  implicit protected override val timeout = Timeout(20.seconds)

  override def currentQuote(market: Market): Future[Quote] =
    AskPattern(peer, QuoteRequest(market)).withReply[Quote]()

  override def openOrders(market: Market) =
    AskPattern(peer, OpenOrdersRequest(market))
      .withReply[OpenOrders]()
      .map(_.orders.entries.toSet)
}
