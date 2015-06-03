package coinffeine.model.payment

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}

trait PaymentProcessor {

  def calculateFee[C <: FiatCurrency](amount: CurrencyAmount[C]): CurrencyAmount[C]

  def amountPlusFee[C <: FiatCurrency](amount: CurrencyAmount[C]): CurrencyAmount[C] =
    amount + calculateFee(amount)

  def amountMinusFee[C <: FiatCurrency](amount: CurrencyAmount[C]): CurrencyAmount[C] =
    amount - calculateFee(amount)

  /** Best step size balancing fees paid and risk taken for this payment processor and currency. */
  def bestStepSize[C <: FiatCurrency](currency: C): CurrencyAmount[C]
}

object PaymentProcessor {
  /** The ID of the payment processor. */
  type Id = String

  /** The ID type of a user account in the payment processor. */
  type AccountId = String

  /** The credentials of a user account in the payment processor. */
  type AccountCredentials = String

  /** The ID type of a payment registered by the payment processor. */
  type PaymentId = String

  /** The type of a invoice associated with a transaction in the payment processor. */
  type Invoice = String
}
