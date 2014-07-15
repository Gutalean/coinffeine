package coinffeine.model.payment

object PaymentProcessor {
  /** The ID of the payment processor. */
  type Id = String

  /** The ID type of a user account in the payment processor. */
  type AccountId = String

  /** The credentials of a user account in the payment processor. */
  type AccountCredentials = String

  /** The ID type of a payment registered by the payment processor. */
  type PaymentId = String
}
