package coinffeine.peer.api.event

import coinffeine.model.currency.BitcoinAmount

/** An event triggered when wallet balance changes. */
case class WalletBalanceChangeEvent(balance: BitcoinAmount) extends CoinffeineAppEvent {

  override val eventType = CoinffeineAppEvent.Info
  override val summary = s"Wallet balance is now $balance"
}
