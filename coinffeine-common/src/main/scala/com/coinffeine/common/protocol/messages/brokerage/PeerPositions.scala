package com.coinffeine.common.protocol.messages.brokerage

import com.coinffeine.common.{FiatCurrency, Order}
import com.coinffeine.common.protocol.messages.PublicMessage

/** Represents the set of orders placed by a peer for a given market. */
case class PeerPositions(market: Market[FiatCurrency], positions: Set[Order]) extends PublicMessage {
  require(positions.forall(_.price.currency == market.currency), s"Mixed currencies in $this")
}
