package coinffeine.peer.events.bitcoin

import coinffeine.model.currency.BitcoinBalance

/** An event reporting the bitcoin balance has changed. */
case class BitcoinBalanceChanged(balance: BitcoinBalance)

object BitcoinBalanceChanged {
  val Topic = "bitcoin.wallet.balance-changed"
}
