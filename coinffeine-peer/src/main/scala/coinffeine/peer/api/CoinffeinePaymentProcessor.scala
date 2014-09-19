package coinffeine.peer.api

import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.CurrencyAmount
import coinffeine.model.payment.PaymentProcessor
import coinffeine.peer.payment.PaymentProcessorProperties

/** Represents how the app interact with a payment processor */
trait CoinffeinePaymentProcessor extends PaymentProcessorProperties {

  def accountId: PaymentProcessor.AccountId

  /** Get the current balance if possible */
  def currentBalance(): Option[CoinffeinePaymentProcessor.Balance]
}

object CoinffeinePaymentProcessor {

  case class Balance(
      totalFunds: CurrencyAmount[Euro.type],
      blockedFunds: CurrencyAmount[Euro.type]) {
    val availableFunds = totalFunds - blockedFunds
  }
}
