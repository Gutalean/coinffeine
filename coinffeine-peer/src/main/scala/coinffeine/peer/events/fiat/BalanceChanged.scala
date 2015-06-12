package coinffeine.peer.events.fiat

import coinffeine.common.akka.event.TopicProvider
import coinffeine.model.currency.AnyFiatBalance

/** An event published when FIAT balance changes. */
case class BalanceChanged(balance: AnyFiatBalance)

object BalanceChanged extends TopicProvider[BalanceChanged] {
  override val Topic = "fiat.balance-changed"
}
