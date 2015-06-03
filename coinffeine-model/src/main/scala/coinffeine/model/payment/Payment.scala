package coinffeine.model.payment

import org.joda.time.DateTime

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.payment.PaymentProcessor._

case class Payment[C <: FiatCurrency](
  id: String,
  senderId: String,
  receiverId: String,
  amount: CurrencyAmount[C],
  date: DateTime,
  description: String,
  invoice: Invoice,
  completed: Boolean
)
