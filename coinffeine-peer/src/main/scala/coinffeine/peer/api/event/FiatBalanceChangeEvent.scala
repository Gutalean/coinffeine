package coinffeine.peer.api.event

import coinffeine.model.currency.FiatAmount

/** Event triggered when a fiat balance change is detected. */
case class FiatBalanceChangeEvent(balance: FiatAmount) extends CoinffeineAppEvent
