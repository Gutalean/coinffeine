package com.coinffeine.client.app

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import akka.actor.ActorRef
import akka.pattern._

import coinffeine.model.currency.FiatCurrency
import coinffeine.protocol.messages.brokerage._
import com.coinffeine.client.api.MarketStats

private[app] class DefaultMarketStats(override val peer: ActorRef)
  extends MarketStats with PeerActorWrapper {

  override def currentQuote[C <: FiatCurrency](market: Market[C]): Future[Quote[C]] =
    (peer ? QuoteRequest(market)).mapTo[Quote[C]]

  override def openOrders[C <: FiatCurrency](market: Market[C]) =
    (peer ? OpenOrdersRequest(market)).mapTo[OpenOrders[C]].map(_.orders.entries.toSet)
}
