package coinffeine.peer.api.event

import coinffeine.model.currency.Currency.Bitcoin

/** An event triggered when wallet balance changes. */
case class WalletBalanceChangeEvent(balance: Balance[Bitcoin.type]) extends CoinffeineAppEvent
