package coinffeine.model.payment.okpay

import org.joda.time.DateTime

import coinffeine.model.currency.FiatAmount
import coinffeine.model.payment.Payment
import coinffeine.model.payment.PaymentProcessor._

case class Transaction(
    transactionId: Long,
    override val senderId: String,
    override val receiverId: String,
    override val netAmount: FiatAmount,
    override val fee: FiatAmount,
    override val date: DateTime,
    override val description: String,
    override val invoice: Invoice,
    status: TransactionStatus = TransactionStatus.Completed) extends Payment {

  require(netAmount.currency == fee.currency,
    s"Amount and fee should have the same currency: $this")

  override val paymentId = transactionId.toString

  override val completed = status.isCompleted
}
