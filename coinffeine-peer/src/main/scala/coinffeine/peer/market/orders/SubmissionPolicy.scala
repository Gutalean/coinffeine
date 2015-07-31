package coinffeine.peer.market.orders

import coinffeine.model.currency.balance.{BitcoinBalance, FiatBalance}
import coinffeine.model.market.OrderBookEntry
import coinffeine.model.util.Cached

trait SubmissionPolicy {
  def currentEntry: Option[OrderBookEntry]
  def setEntry(entry: OrderBookEntry): Unit
  def unsetEntry(): Unit
  def setBitcoinBalance(balance: BitcoinBalance): Unit
  def setFiatBalance(balance: Cached[FiatBalance]): Unit
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

  override def setBitcoinBalance(balance: BitcoinBalance): Unit = {}

  override def setFiatBalance(balance: Cached[FiatBalance]): Unit = {}

  override def entryToSubmit: Option[OrderBookEntry] = currentEntry
}
