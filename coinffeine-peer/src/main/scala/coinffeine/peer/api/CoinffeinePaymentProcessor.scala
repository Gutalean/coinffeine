package coinffeine.peer.api

import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.CurrencyAmount

/** Represents how the app interact with a payment processor */
trait CoinffeinePaymentProcessor {
  /** Get the current balance if possible */
  def currentBalance(): Option[CurrencyAmount[Euro.type]]
}
