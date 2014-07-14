package com.coinffeine.common.protocol.messages.brokerage

import com.coinffeine.common.{CurrencyAmount, FiatCurrency, Order}
import com.coinffeine.common.protocol.messages.PublicMessage

/** Represents the set of orders placed by a peer for a given market. */
case class PeerOrderRequests[+C <: FiatCurrency](
    market: Market[C], positions: Seq[Order[CurrencyAmount[C]]]) extends PublicMessage {
  require(positions.forall(_.price.currency == market.currency), s"Mixed currencies in $this")

  def addOrder[B >: C <: FiatCurrency](order: Order[CurrencyAmount[B]]): PeerOrderRequests[B] =
    copy(positions = positions :+ order)
}

object PeerOrderRequests {

  def empty[C <: FiatCurrency](market: Market[C]): PeerOrderRequests[C] =
    PeerOrderRequests(market, Seq.empty)
}
