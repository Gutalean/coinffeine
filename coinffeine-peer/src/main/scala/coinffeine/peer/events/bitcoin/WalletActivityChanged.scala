package coinffeine.peer.events.bitcoin

import coinffeine.common.akka.event.TopicProvider
import coinffeine.model.bitcoin.WalletActivity

/** An event reporting changes in the wallet activity. */
case class WalletActivityChanged(activity: WalletActivity)

object WalletActivityChanged extends TopicProvider[WalletActivityChanged] {
  override val Topic = "bitcoin.wallet.wallet-activity-changed"
}
