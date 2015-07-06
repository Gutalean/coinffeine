package coinffeine.model.payment.okpay

import org.joda.time.DateTime

import coinffeine.model.currency.{FiatAmount, FiatCurrency}
import coinffeine.model.payment.PaymentProcessor._

case class Transaction(
    id: Long,
    senderId: String,
    receiverId: String,
    netAmount: FiatAmount,
    fee: FiatAmount,
    date: DateTime,
    description: String,
    invoice: Invoice,
    status: TransactionStatus = TransactionStatus.Completed) {

  require(netAmount.currency == fee.currency,
    s"Amount and fee should have the same currency: $this")

  def currency: FiatCurrency = netAmount.currency

  def grossAmount: FiatAmount = netAmount + fee
}
