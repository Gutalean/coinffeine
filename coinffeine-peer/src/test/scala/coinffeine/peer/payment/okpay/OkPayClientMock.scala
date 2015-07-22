package coinffeine.peer.payment.okpay

import scala.concurrent.{ExecutionContext, Future}

import coinffeine.model.currency.{FiatAmount, FiatAmounts}
import coinffeine.model.payment.Payment
import coinffeine.model.payment.PaymentProcessor._

class OkPayClientMock(override val accountId: AccountId) extends OkPayClient {

  private val balances = new Fallible(FiatAmounts.empty)
  private val remainingLimits = new Fallible(FiatAmounts.empty)
  private val payments = new Fallible(Map.empty[PaymentId, Payment])
  private var paymentResult: Future[Payment] = _

  override val executionContext = ExecutionContext.global

  override def currentBalances(): Future[FiatAmounts] = balances.lookup()

  override def currentRemainingLimits(): Future[FiatAmounts] = remainingLimits.lookup()

  override def findPaymentById(paymentId: PaymentId): Future[Option[Payment]] =
    payments.lookup(_.get(paymentId))

  override def findPaymentByInvoice(invoice: Invoice): Future[Option[Payment]] =
    payments.lookup(_.values.find(_.invoice == invoice))

  override def sendPayment(
      to: AccountId,
      amount: FiatAmount,
      comment: String,
      invoice: Invoice,
      feePolicy: FeePolicy): Future[Payment] = paymentResult

  override def checkExistence(id: AccountId): Future[Boolean] = ???

  def givenBalancesCannotBeRetrieved(cause: Throwable): Unit = synchronized {
    balances.givenLookupWillFail(cause)
  }

  def givenBalancesCanBeRetrieved(): Unit = synchronized {
    balances.givenLookupWillSucceed()
  }

  def givenBalances(newBalances: FiatAmounts): Unit = synchronized {
    balances.givenValue(newBalances)
  }

  def givenLimitsCannotBeRetrieved(cause: Throwable): Unit = synchronized {
    remainingLimits.givenLookupWillFail(cause)
  }

  def givenLimitsCanBeRetrieved(): Unit = synchronized {
    remainingLimits.givenLookupWillSucceed()
  }

  def givenLimits(newRemainingLimits: FiatAmounts): Unit = synchronized {
    remainingLimits.givenValue(newRemainingLimits)
  }

  def givenPaymentsCannotBeRetrieved(cause: Throwable): Unit = synchronized {
    payments.givenLookupWillFail(cause)
  }

  def givenPaymentsCanBeRetrieved(): Unit = synchronized {
    payments.givenLookupWillSucceed()
  }

  def givenExistingPayment(payment: Payment): Unit = synchronized {
    payments.givenValue(payments.currentValue + (payment.paymentId -> payment))
  }

  def givenPaymentResult(result: Future[Payment]): Unit = synchronized {
    paymentResult = result
  }
}

private class Fallible[A](initialValue: A) {
  private var value: A = initialValue
  private var retrievalFailure: Option[Throwable] = None

  def givenLookupWillFail(cause: Throwable): Unit = {
    retrievalFailure = Some(cause)
  }

  def givenLookupWillSucceed(): Unit = {
    retrievalFailure = None
  }

  def givenValue(newValue: A): Unit = {
    value = newValue
  }

  def currentValue: A = value

  def lookup(): Future[A] = lookup(identity)

  def lookup[B](f: A => B): Future[B] =
    retrievalFailure.fold(Future.successful(f(value)))(Future.failed)
}
