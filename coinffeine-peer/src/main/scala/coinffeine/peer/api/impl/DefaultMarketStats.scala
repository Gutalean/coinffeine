package coinffeine.peer.api.impl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.util.Timeout

import coinffeine.common.akka.AskPattern
import coinffeine.model.currency.FiatCurrency
import coinffeine.peer.api.MarketStats
import coinffeine.protocol.messages.brokerage._

private[impl] class DefaultMarketStats(override val peer: ActorRef)
  extends MarketStats with PeerActorWrapper {

  implicit protected override val timeout = Timeout(20.seconds)

  override def currentQuote[C <: FiatCurrency](market: Market[C]): Future[Quote[C]] =
    AskPattern(peer, QuoteRequest(market)).withReply[Quote[C]]()

  override def openOrders[C <: FiatCurrency](market: Market[C]) =
    AskPattern(peer, OpenOrdersRequest(market))
      .withReply[OpenOrders[C]]()
      .map(_.orders.entries.toSet)
}
