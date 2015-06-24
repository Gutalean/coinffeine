package coinffeine.peer.payment.okpay

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import coinffeine.model.currency.{CurrencyAmount, FiatAmount, FiatCurrency}
import coinffeine.model.payment.PaymentProcessor._
import coinffeine.model.payment.{AnyPayment, Payment}
import coinffeine.peer.payment.okpay.OkPayClient.FeePolicy

class OkPayClientMock(override val accountId: AccountId) extends OkPayClient {

  private var balances: Future[Seq[FiatAmount]] = Future.successful(Seq.empty)
  private var paymentResult: Future[AnyPayment] = _
  private var payments: Map[PaymentId, Try[Option[AnyPayment]]] = Map.empty

  override val executionContext = ExecutionContext.global

  override def currentBalances(): Future[Seq[FiatAmount]] = balances

  override def findPaymentById(paymentId: PaymentId): Future[Option[AnyPayment]] =
    Future.fromTry(payments(paymentId))

  override def findPaymentByInvoice(invoice: Invoice): Future[Option[AnyPayment]] =
    Future.successful(payments.values.collectFirst {
      case Success(Some(payment)) if payment.invoice == invoice => payment
    })

  override def sendPayment[C <: FiatCurrency](to: AccountId,
                                              amount: CurrencyAmount[C],
                                              comment: String,
                                              invoice: Invoice,
                                              feePolicy: FeePolicy): Future[Payment[C]] =
    paymentResult.asInstanceOf[Future[Payment[C]]]

  def setBalances(balances: Seq[FiatAmount]): Unit = {
    setBalances(Future.successful(balances))
  }

  def setBalances(newBalances: Future[Seq[FiatAmount]]): Unit =
    synchronized { balances = newBalances }

  def setPaymentResult(result: Future[AnyPayment]): Unit =
    synchronized { paymentResult = result }

  def givenExistingPayment(payment: AnyPayment): Unit =
    synchronized { payments += payment.id -> Success(Some(payment)) }

  def givenNonExistingPayment(paymentId: PaymentId): Unit =
    synchronized { payments += paymentId -> Success(None) }

  def givenPaymentCannotBeRetrieved(paymentId: PaymentId, error: Throwable): Unit =
    synchronized { payments += paymentId -> Failure(error) }
}
