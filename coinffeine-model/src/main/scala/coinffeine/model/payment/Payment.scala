package coinffeine.model.payment

import org.joda.time.DateTime

import coinffeine.model.currency.{FiatAmount, FiatCurrency}
import coinffeine.model.payment.PaymentProcessor._

trait Payment {
  val paymentId: String
  val senderId: String
  val receiverId: String
  val netAmount: FiatAmount
  val fee: FiatAmount
  val date: DateTime
  val description: String
  val invoice: Invoice
  val completed: Boolean

  def currency: FiatCurrency = netAmount.currency

  def grossAmount: FiatAmount = netAmount + fee
}
