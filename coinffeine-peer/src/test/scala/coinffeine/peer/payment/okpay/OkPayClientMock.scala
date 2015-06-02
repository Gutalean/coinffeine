package coinffeine.peer.payment.okpay

import scala.concurrent.{ExecutionContext, Future}

import coinffeine.model.currency.{FiatAmount, CurrencyAmount, FiatCurrency}
import coinffeine.model.payment.{AnyPayment, Payment}
import coinffeine.model.payment.PaymentProcessor.{PaymentId, AccountId}
import coinffeine.peer.payment.okpay.OkPayClient.FeePolicy

class OkPayClientMock(override val accountId: AccountId) extends OkPayClient {

  private var balances: Future[Seq[FiatAmount]] = _
  private var paymentResult: Future[AnyPayment] = _
  private var payments: Map[PaymentId, Future[Option[AnyPayment]]] = Map.empty

  override val executionContext = ExecutionContext.global

  override def currentBalances(): Future[Seq[FiatAmount]] = balances

  override def findPayment(paymentId: PaymentId): Future[Option[AnyPayment]] = payments(paymentId)

  override def sendPayment[C <: FiatCurrency](to: AccountId,
                                              amount: CurrencyAmount[C],
                                              comment: String,
                                              invoice: String,
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
    synchronized { payments += payment.id -> Future.successful(Some(payment)) }

  def givenNonExistingPayment(paymentId: PaymentId): Unit =
    synchronized { payments += paymentId -> Future.successful(None) }

  def givenPaymentCannotBeRetrieved(paymentId: PaymentId, error: Throwable): Unit =
    synchronized { payments += paymentId -> Future.failed(error) }
}
