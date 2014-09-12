package coinffeine.model.event

import coinffeine.model.currency.{Balance, FiatCurrency}

/** Event triggered when a fiat balance change is detected. */
case class FiatBalanceChangeEvent(balance: Balance[_ <: FiatCurrency]) extends CoinffeineAppEvent
