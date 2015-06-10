package coinffeine.peer.properties.bitcoin

import akka.actor.ActorSystem

import coinffeine.common.akka.event.EventObservedProperty
import coinffeine.model.bitcoin.{Address, WalletActivity, WalletProperties}
import coinffeine.model.currency.BitcoinBalance
import coinffeine.peer.events.bitcoin._

class DefaultWalletProperties(implicit system: ActorSystem) extends WalletProperties {

  override val balance = EventObservedProperty[Option[BitcoinBalance]](
      BitcoinBalanceChanged.Topic, None) {
    case BitcoinBalanceChanged(b) => Some(b)
  }

  override val activity = EventObservedProperty[WalletActivity](
      WalletActivityChanged.Topic, WalletActivity()) {
    case WalletActivityChanged(a) => a
  }

  override val primaryAddress = EventObservedProperty[Option[Address]](
      PrimaryAddressChanged.Topic, None) {
    case PrimaryAddressChanged(addr) => Some(addr)
  }
}
