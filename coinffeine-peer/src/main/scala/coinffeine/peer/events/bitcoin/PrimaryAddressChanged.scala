package coinffeine.peer.events.bitcoin

import coinffeine.common.akka.event.TopicProvider
import coinffeine.model.bitcoin.Address

/** An event reporting the primary bitcoin address has changed. */
case class PrimaryAddressChanged(address: Address)

object PrimaryAddressChanged extends TopicProvider[PrimaryAddressChanged] {
  override val Topic = "bitcoin.wallet.primary-address-changed"
}
