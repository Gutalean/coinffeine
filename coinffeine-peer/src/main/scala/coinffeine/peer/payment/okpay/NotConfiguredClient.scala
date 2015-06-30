package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scala.util.control.NoStackTrace

import coinffeine.model.currency.FiatAmount
import coinffeine.model.payment.PaymentProcessor._

/** Null object pattern for the case of not having enough information to configure an
  * actual OK Pay client.
  */
object NotConfiguredClient extends OkPayClient {
  override val accountId = "unconfigured"

  override def findPaymentById(paymentId: PaymentId) = Future.failed(notConfiguredError)

  override def findPaymentByInvoice(invoice: Invoice) = Future.failed(notConfiguredError)

  override def currentBalances() = Future.failed(notConfiguredError)

  override def sendPayment(
      to: AccountId, amount: FiatAmount, comment: String, invoice: Invoice,
      feePolicy: FeePolicy) =
    Future.failed(notConfiguredError)

  override protected def executionContext = throw notConfiguredError

  private val notConfiguredError =
    new IllegalStateException("OKPay credentials are not properly configured")
        with NoStackTrace
}
