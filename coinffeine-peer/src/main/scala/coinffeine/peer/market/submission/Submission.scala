package coinffeine.peer.market.submission

import akka.actor.ActorRef

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{OrderId, Market, OrderBookEntry}
import coinffeine.protocol.messages.brokerage.PeerPositions

/** Collection of entries to be submitted and requester actors */
private case class Submission[C <: FiatCurrency](market: Market[C],
                                                 entries: Seq[Submission.Entry[C]]) {
  require(entries.map(_.orderBookEntry.id).toSet.size == entries.size, s"Repeated orders: $entries")

  def toPeerPositions: PeerPositions[C] = PeerPositions(market, entries.map(_.orderBookEntry))

  def addEntry(requester: ActorRef, orderBookEntry: OrderBookEntry[C]): Submission[C] =
    removeEntry(orderBookEntry.id).updateEntries(_ :+ Submission.Entry(requester, orderBookEntry))

  def removeEntry(orderId: OrderId): Submission[C] =
    updateEntries(_.filterNot(_.orderBookEntry.id == orderId))

  private def updateEntries(f: Seq[Submission.Entry[C]] => Seq[Submission.Entry[C]]): Submission[C] =
    copy(entries = f(entries))
}

private object Submission {

  def empty[C <: FiatCurrency](market: Market[C]): Submission[C] =
    Submission(market, entries = Seq.empty)

  /** Each entry to be submitted to the order book */
  case class Entry[C <: FiatCurrency](requester: ActorRef, orderBookEntry: OrderBookEntry[C])
}

