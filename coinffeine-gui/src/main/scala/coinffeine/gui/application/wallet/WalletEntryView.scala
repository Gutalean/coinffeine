package coinffeine.gui.application.wallet

import org.joda.time.DateTime

import coinffeine.gui.control.GlyphIcon
import coinffeine.model.bitcoin.WalletActivity.EntryType
import coinffeine.model.bitcoin.{Hash, WalletActivity}

sealed abstract class WalletEntryView(entry: WalletActivity.Entry) {
  def timestamp: DateTime = entry.time
  def hash: Hash = entry.tx.get.getHash
  def icon: GlyphIcon
  def summary: String
}

object WalletEntryView {

  def forEntry(entry: WalletActivity.Entry): WalletEntryView = entry.entryType match {
    case EntryType.InFlow => new InFlowView(entry)
    case EntryType.OutFlow => new OutFlowView(entry)
    case EntryType.DepositLock(_) => new DepositLockView(entry)
    case EntryType.DepositUnlock(_) => new DepositUnlockView(entry)
  }

  class InFlowView(entry: WalletActivity.Entry) extends WalletEntryView(entry) {
    override def icon = GlyphIcon.BitcoinInflow
    override def summary = s"${entry.amount.abs} added to your wallet"
  }

  class OutFlowView(entry: WalletActivity.Entry) extends WalletEntryView(entry) {
    override def icon = GlyphIcon.BitcoinOutflow
    override def summary = s"${entry.amount.abs} withdrawn from your wallet"
  }

  class DepositLockView(entry: WalletActivity.Entry) extends WalletEntryView(entry) {
    override def icon = GlyphIcon.ExchangeTypes
    override def summary = s"${entry.amount} locked for an exchange"
  }

  class DepositUnlockView(entry: WalletActivity.Entry) extends WalletEntryView(entry) {
    override def icon = GlyphIcon.ExchangeTypes
    override def summary = s"${entry.amount} unlocked from an exchange"
  }
}
