package coinffeine.model.payment

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}

object PaymentProcessor {
  /** The ID of the payment processor. */
  type Id = String

  /** The ID type of a user account in the payment processor. */
  type AccountId = String

  /** The credentials of a user account in the payment processor. */
  type AccountCredentials = String

  /** The ID type of a payment registered by the payment processor. */
  type PaymentId = String

  /** Identifier of some funds blocked for a specific use. */
  case class BlockedFundsId(underlying: Int) extends AnyVal

  trait FeeCalculator {

    def calculateFee[C <: FiatCurrency](amount: CurrencyAmount[C]): CurrencyAmount[C]

    def amountPlusFee[C <: FiatCurrency](amount: CurrencyAmount[C]): CurrencyAmount[C] =
      amount + calculateFee(amount)

    def amountMinusFee[C <: FiatCurrency](amount: CurrencyAmount[C]): CurrencyAmount[C] =
      amount - calculateFee(amount)
  }
}
