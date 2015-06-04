package coinffeine.peer.payment.okpay

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future
import scala.util.control.NoStackTrace

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.payment.PaymentProcessor._
import coinffeine.peer.payment.okpay.OkPayClient.FeePolicy
import coinffeine.peer.payment.okpay.ws.{OkPayWebService, OkPayWebServiceClient}

class OkPayClientFactory(lookupSettings: () => OkPaySettings)
  extends OkPayProcessorActor.ClientFactory {

  private val okPay = new OkPayWebService(lookupSettings().serverEndpointOverride)
  private val tokenCache = new AtomicReference[Option[String]](None)

  object NotConfiguredClient extends OkPayClient {
    private val error = new IllegalStateException(
      "OKPay's user id and/or seed token are not configured") with NoStackTrace
    override lazy val accountId: AccountId = throw error
    override def findPaymentById(paymentId: PaymentId) = Future.failed(error)
    override def findPaymentByInvoice(invoice: Invoice) = Future.failed(error)
    override def currentBalances() = Future.failed(error)
    override def sendPayment[C <: FiatCurrency](
        to: AccountId, amount: CurrencyAmount[C], comment: String,
        invoice: Invoice, feePolicy: FeePolicy) =
      Future.failed(error)
    override protected def executionContext = throw error
  }

  override def build(): OkPayClient = {
    val settings = lookupSettings()
    settings.apiCredentials.fold[OkPayClient](NotConfiguredClient) { credentials =>
      new OkPayWebServiceClient(
        okPay.service, tokenCache, credentials.walletId, credentials.seedToken)
    }
  }

  override def shutdown(): Unit = {
    okPay.httpClient.shutdown()
  }
}
