package coinffeine.model.bitcoin

import org.joda.time.DateTime

import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId

case class WalletActivity(entries: Seq[WalletActivity.Entry] = Seq.empty)

object WalletActivity {

  sealed trait EntryType
  object EntryType {
    case object InFlow extends EntryType
    case object OutFlow extends EntryType
    case object Deposit extends EntryType
  }

  case class Entry(time: DateTime,
                   tx: ImmutableTransaction,
                   amount: BitcoinAmount,
                   exchangeId: Option[ExchangeId]) {
    def entryType: EntryType =
      if (exchangeId.isDefined) EntryType.Deposit
      else if (amount.isNegative) EntryType.OutFlow
      else EntryType.InFlow
  }

  def apply(
      deposits: Map[Hash, ExchangeId],
      wallet: Wallet,
      transactions: MutableTransaction*): WalletActivity =
    WalletActivity(transactions.map { tx =>
      val exchangeId = deposits.get(tx.getHash)
      Entry(
        time = new DateTime(tx.getUpdateTime),
        tx = ImmutableTransaction(tx),
        amount =
          if (exchangeId.isDefined) tx.getValueSentFromMe(wallet)
          else tx.getValue(wallet),
        exchangeId = exchangeId
      )
    })
}
