package coinffeine.peer.events.bitcoin

import coinffeine.common.akka.event.TopicProvider
import coinffeine.model.currency.BitcoinBalance

/** An event reporting the bitcoin balance has changed. */
case class BitcoinBalanceChanged(balance: BitcoinBalance)

object BitcoinBalanceChanged extends TopicProvider[BitcoinBalanceChanged]{
  override val Topic = "bitcoin.wallet.balance-changed"
}
