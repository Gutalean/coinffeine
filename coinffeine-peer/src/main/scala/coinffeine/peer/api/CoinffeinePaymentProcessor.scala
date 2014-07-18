package coinffeine.peer.api

import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.CurrencyAmount

/** Represents how the app interact with a payment processor */
trait CoinffeinePaymentProcessor {
  def currentBalance(): CurrencyAmount[Euro.type]
}
