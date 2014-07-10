package com.coinffeine.common.protocol.messages.brokerage

import com.coinffeine.common.{CurrencyAmount, FiatCurrency, Order}
import com.coinffeine.common.protocol.messages.PublicMessage

/** Represents the set of orders placed by a peer for a given market. */
case class PeerPositions[+C <: FiatCurrency](
    market: Market[C], positions: Seq[Order[CurrencyAmount[C]]]) extends PublicMessage {
  require(positions.forall(_.price.currency == market.currency), s"Mixed currencies in $this")
}
