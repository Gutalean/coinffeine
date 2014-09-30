package coinffeine.model.bitcoin

import com.google.bitcoin.core.Transaction
import org.joda.time.DateTime

import coinffeine.model.currency._

case class WalletActivity(entries: Seq[WalletActivity.Entry] = Seq.empty)

object WalletActivity {

  case class Entry(time: DateTime,
                   tx: ImmutableTransaction,
                   amount: Bitcoin.Amount)

  def apply(wallet: Wallet, transactions: Transaction*): WalletActivity = WalletActivity(
    transactions.map{ tx =>
      Entry(
        time = new DateTime(tx.getUpdateTime),
        tx = ImmutableTransaction(tx),
        amount = Bitcoin.fromSatoshi(tx.getValue(wallet))
      )
    }
  )
}
