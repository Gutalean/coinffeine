package coinffeine.model.bitcoin

import scala.collection.JavaConverters._

import org.joda.time.DateTime

import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId

case class WalletActivity(entries: Seq[WalletActivity.Entry] = Seq.empty)

object WalletActivity {

  sealed trait EntryType
  object EntryType {
    case object InFlow extends EntryType
    case object OutFlow extends EntryType
    case class DepositLock(id: ExchangeId) extends EntryType
    case class DepositUnlock(id: ExchangeId) extends EntryType
  }

  case class Entry(
      entryType: EntryType,
      time: DateTime,
      tx: ImmutableTransaction,
      amount: BitcoinAmount)

  def apply(
      deposits: Map[Hash, ExchangeId],
      wallet: Wallet,
      transactions: MutableTransaction*): WalletActivity = {

    def toEntry(tx: MutableTransaction): Entry =
      createsDeposit(tx).map(deposit => toDepositLockEntry(deposit, tx)) orElse
          spendsDeposit(tx).map(deposit => toDepositUnlockEntry(deposit, tx)) getOrElse
          toRegularEntry(tx)

    def createsDeposit(tx: MutableTransaction): Option[ExchangeId] = deposits.get(tx.getHash)

    def toDepositLockEntry(deposit: ExchangeId, tx: MutableTransaction): Entry = entry(
      entryType = EntryType.DepositLock(deposit),
      tx = tx,
      amount = tx.getValueSentFromMe(wallet)
    )

    def spendsDeposit(tx: MutableTransaction): Option[ExchangeId] = {
      val spentCandidates = tx.getInputs.asScala
        .map(_.getOutpoint)
        .filter(_.getIndex == 0) // only output #0 spends deposit; others are change outputs
        .map(_.getHash)
        .toSet
      deposits.collectFirst {
        case (deposit, id) if spentCandidates.contains(deposit) => id
      }
    }

    def toDepositUnlockEntry(deposit: ExchangeId, tx: MutableTransaction): Entry = entry(
      entryType = EntryType.DepositUnlock(deposit),
      tx = tx,
      amount = tx.getValueSentToMe(wallet)
    )

    def toRegularEntry(tx: MutableTransaction): Entry = {
      val amount = tx.getValue(wallet)
      val entryType = if (amount.isNegative) EntryType.OutFlow else EntryType.InFlow
      entry(entryType, tx, amount)
    }

    def entry(entryType: EntryType, tx: MutableTransaction, amount: Bitcoin.Amount) = Entry(
      entryType = entryType,
      time = new DateTime(tx.getUpdateTime),
      tx = ImmutableTransaction(tx),
      amount = amount
    )

    WalletActivity(transactions.map(toEntry))
  }
}
