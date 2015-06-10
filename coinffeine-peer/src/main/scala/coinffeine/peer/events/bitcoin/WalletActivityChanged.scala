package coinffeine.peer.events.bitcoin

import coinffeine.model.bitcoin.WalletActivity

/** An event reporting changes in the wallet activity. */
case class WalletActivityChanged(activity: WalletActivity)

object WalletActivityChanged {
  val Topic = "bitcoin.wallet.wallet-activity-changed"
}
