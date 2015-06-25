package coinffeine.protocol.messages.brokerage

import java.util.UUID

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{Market, OrderBookEntry}
import coinffeine.protocol.messages.PublicMessage

/** Represents the set of orders placed by a peer for a given market. */
case class PeerPositions(
    market: Market,
    entries: Seq[OrderBookEntry],
    nonce: PeerPositions.Nonce = PeerPositions.createNonce()) extends PublicMessage {
  require(entries.forall(_.price.currency == market.currency), s"Mixed currencies in $this")
  require(entries.map(_.id).toSet.size == entries.size, s"Repeated order ids: $entries")

  def addEntry(order: OrderBookEntry): PeerPositions =
    copy(entries = entries :+ order, nonce = PeerPositions.createNonce())
}

object PeerPositions {

  type Nonce = String

  def empty(market: Market): PeerPositions =
    PeerPositions(market, Seq.empty)

  def createNonce() = UUID.randomUUID().toString
}
