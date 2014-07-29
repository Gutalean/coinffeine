package coinffeine.protocol.messages.brokerage

import java.util.UUID

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.market.OrderBookEntry
import coinffeine.protocol.messages.PublicMessage

/** Represents the set of orders placed by a peer for a given market. */
case class PeerPositions[+C <: FiatCurrency](
    market: Market[C],
    entries: Seq[OrderBookEntry[CurrencyAmount[C]]],
    nonce: PeerPositions.Nonce = PeerPositions.createNonce()) extends PublicMessage {
  require(entries.forall(_.price.currency == market.currency), s"Mixed currencies in $this")

  def addEntry[B >: C <: FiatCurrency](order: OrderBookEntry[CurrencyAmount[B]]): PeerPositions[B] =
    copy(entries = entries :+ order, nonce = PeerPositions.createNonce())
}

object PeerPositions {

  type Nonce = String

  def empty[C <: FiatCurrency](market: Market[C]): PeerPositions[C] =
    PeerPositions(market, Seq.empty)

  def createNonce() = UUID.randomUUID().toString
}
