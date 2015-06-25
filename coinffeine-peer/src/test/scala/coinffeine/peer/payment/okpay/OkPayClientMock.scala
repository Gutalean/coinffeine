package coinffeine.peer.payment.okpay

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import coinffeine.model.currency.FiatAmount
import coinffeine.model.payment.Payment
import coinffeine.model.payment.PaymentProcessor._
import coinffeine.peer.payment.okpay.OkPayClient.FeePolicy

class OkPayClientMock(override val accountId: AccountId) extends OkPayClient {

  private var balances: Future[Seq[FiatAmount]] = Future.successful(Seq.empty)
  private var paymentResult: Future[Payment] = _
  private var payments: Map[PaymentId, Try[Option[Payment]]] = Map.empty

  override val executionContext = ExecutionContext.global

  override def currentBalances(): Future[Seq[FiatAmount]] = balances

  override def findPaymentById(paymentId: PaymentId): Future[Option[Payment]] =
    Future.fromTry(payments(paymentId))

  override def findPaymentByInvoice(invoice: Invoice): Future[Option[Payment]] =
    Future.successful(payments.values.collectFirst {
      case Success(Some(payment)) if payment.invoice == invoice => payment
    })

  override def sendPayment(
      to: AccountId,
      amount: FiatAmount,
      comment: String,
      invoice: Invoice,
      feePolicy: FeePolicy): Future[Payment] = paymentResult

  def setBalances(balances: Seq[FiatAmount]): Unit = {
    setBalances(Future.successful(balances))
  }

  def setBalances(newBalances: Future[Seq[FiatAmount]]): Unit = synchronized {
    balances = newBalances
  }

  def setPaymentResult(result: Future[Payment]): Unit = synchronized {
    paymentResult = result
  }

  def givenExistingPayment(payment: Payment): Unit = synchronized {
    payments += payment.id -> Success(Some(payment))
  }

  def givenNonExistingPayment(paymentId: PaymentId): Unit = synchronized {
    payments += paymentId -> Success(None)
  }

  def givenPaymentCannotBeRetrieved(paymentId: PaymentId, error: Throwable): Unit =
    synchronized {
      payments += paymentId -> Failure(error)
    }
}
