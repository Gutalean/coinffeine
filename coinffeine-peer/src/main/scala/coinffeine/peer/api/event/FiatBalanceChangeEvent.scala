package coinffeine.peer.api.event

import coinffeine.model.currency.FiatAmount

/** Event triggered when a fiat balance change is detected. */
case class FiatBalanceChangeEvent(balance: FiatAmount) extends CoinffeineAppEvent {

  override val eventType = CoinffeineAppEvent.Info
  override val summary = s"Fiat balance changed"
  override val description = s"The fiat balance is now $balance"
}
