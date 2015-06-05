package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scala.util.control.NoStackTrace

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.payment.PaymentProcessor._
import coinffeine.peer.payment.okpay.OkPayClient.FeePolicy

/** Null object pattern for the case of not having enough information to configure an
  * actual OK Pay client.
  */
object NotConfiguredClient extends OkPayClient {
  override val accountId = "unconfigured"

  override def findPaymentById(paymentId: PaymentId) = Future.failed(notConfiguredError)

  override def findPaymentByInvoice(invoice: Invoice) = Future.failed(notConfiguredError)

  override def currentBalances() = Future.failed(notConfiguredError)

  override def sendPayment[C <: FiatCurrency](
    to: AccountId, amount: CurrencyAmount[C], comment: String,
    invoice: Invoice, feePolicy: FeePolicy) =
    Future.failed(notConfiguredError)

  override protected def executionContext = throw notConfiguredError

  private val notConfiguredError =
    new IllegalStateException("OKPay credentials are not properly configured")
      with NoStackTrace
}
