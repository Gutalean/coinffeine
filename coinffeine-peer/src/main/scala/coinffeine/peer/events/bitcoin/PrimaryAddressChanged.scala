package coinffeine.peer.events.bitcoin

import coinffeine.model.bitcoin.Address

/** An event reporting the primary bitcoin address has changed. */
case class PrimaryAddressChanged(address: Address)

object PrimaryAddressChanged {
  val Topic = "bitcoin.wallet.primary-address-changed"
}
