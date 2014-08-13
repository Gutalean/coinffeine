package coinffeine.peer.api.event

import coinffeine.model.currency.FiatCurrency

/** Event triggered when a fiat balance change is detected. */
case class FiatBalanceChangeEvent(balance: Balance[FiatCurrency]) extends CoinffeineAppEvent
