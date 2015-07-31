package coinffeine.peer.market.orders

import coinffeine.model.market.OrderBookEntry

trait SubmissionPolicy {
  def currentEntry: Option[OrderBookEntry]
  def setEntry(entry: OrderBookEntry): Unit
  def unsetEntry(): Unit
  def entryToSubmit: Option[OrderBookEntry]
}

class SubmissionPolicyImpl extends SubmissionPolicy {

  private var entry: Option[OrderBookEntry] = None

  override def currentEntry = entry

  override def setEntry(entry: OrderBookEntry): Unit = {
    this.entry = Some(entry)
  }

  override def unsetEntry(): Unit = {
    this.entry = None
  }

  override def entryToSubmit: Option[OrderBookEntry] = currentEntry
}
