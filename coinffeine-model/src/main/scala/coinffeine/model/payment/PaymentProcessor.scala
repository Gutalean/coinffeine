package coinffeine.model.payment

import coinffeine.model.currency.{FiatAmount, FiatCurrency}

trait PaymentProcessor {

  def calculateFee(amount: FiatAmount): FiatAmount

  def amountPlusFee(amount: FiatAmount): FiatAmount =
    amount + calculateFee(amount)

  def amountMinusFee(amount: FiatAmount): FiatAmount =
    amount - calculateFee(amount)

  /** Best step size balancing fees paid and risk taken for this payment processor and currency. */
  def bestStepSize(currency: FiatCurrency): FiatAmount
}

object PaymentProcessor {
  /** The ID of the payment processor. */
  type Id = String

  /** The ID type of a user account in the payment processor. */
  type AccountId = String

  /** The secret of a user account in the payment processor. Might be a password or a token. */
  type AccountSecret = String

  /** The ID type of a payment registered by the payment processor. */
  type PaymentId = String

  /** The type of a invoice associated with a transaction in the payment processor. */
  type Invoice = String
}
