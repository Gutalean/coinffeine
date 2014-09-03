package coinffeine.peer.api.impl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.pattern._
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import coinffeine.model.currency.FiatCurrency
import coinffeine.peer.api.MarketStats
import coinffeine.protocol.messages.brokerage._

private[impl] class DefaultMarketStats(override val peer: ActorRef)
  extends MarketStats with PeerActorWrapper {

  implicit private val requestTimeout = Timeout(20.seconds)

  override def currentQuote[C <: FiatCurrency](market: Market[C]): Future[Quote[C]] =
    (peer ? QuoteRequest(market)).mapTo[Quote[C]]

  override def openOrders[C <: FiatCurrency](market: Market[C]) =
    (peer ? OpenOrdersRequest(market)).mapTo[OpenOrders[C]].map(_.orders.entries.toSet)
}
