package coinffeine.model.payment

import org.joda.time.DateTime

import coinffeine.model.currency.FiatAmount
import coinffeine.model.payment.PaymentProcessor._

case class Payment(
    id: String,
    senderId: String,
    receiverId: String,
    amount: FiatAmount,
    date: DateTime,
    description: String,
    invoice: Invoice,
    completed: Boolean)
