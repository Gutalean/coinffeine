package com.coinffeine.common.protocol.messages.brokerage

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.market.OrderBookEntry
import com.coinffeine.common.protocol.messages.PublicMessage

/** Represents the set of orders placed by a peer for a given market. */
case class PeerOrderRequests[+C <: FiatCurrency](
    market: Market[C], entries: Seq[OrderBookEntry[CurrencyAmount[C]]]) extends PublicMessage {
  require(entries.forall(_.price.currency == market.currency), s"Mixed currencies in $this")

  def addEntry[B >: C <: FiatCurrency](order: OrderBookEntry[CurrencyAmount[B]]): PeerOrderRequests[B] =
    copy(entries = entries :+ order)
}

object PeerOrderRequests {

  def empty[C <: FiatCurrency](market: Market[C]): PeerOrderRequests[C] =
    PeerOrderRequests(market, Seq.empty)
}
