package coinffeine.peer.market.submission

import akka.actor.ActorRef

import coinffeine.model.market.{Market, OrderBookEntry}
import coinffeine.model.order.OrderId
import coinffeine.protocol.messages.brokerage.PeerPositions

/** Collection of entries to be submitted and requester actors */
private case class Submission(
    market: Market,
    entries: Seq[Submission.Entry]) {
  require(entries.map(_.orderBookEntry.id).toSet.size == entries.size, s"Repeated orders: $entries")

  def toPeerPositions: PeerPositions = PeerPositions(market, entries.map(_.orderBookEntry))

  def addEntry(requester: ActorRef, orderBookEntry: OrderBookEntry): Submission =
    removeEntry(orderBookEntry.id).updateEntries(_ :+ Submission.Entry(requester, orderBookEntry))

  def removeEntry(orderId: OrderId): Submission =
    updateEntries(_.filterNot(_.orderBookEntry.id == orderId))

  private def updateEntries(f: Seq[Submission.Entry] => Seq[Submission.Entry]): Submission =
    copy(entries = f(entries))
}

private object Submission {

  def empty(market: Market): Submission =
    Submission(market, entries = Seq.empty)

  /** Each entry to be submitted to the order book */
  case class Entry(requester: ActorRef, orderBookEntry: OrderBookEntry)

}

