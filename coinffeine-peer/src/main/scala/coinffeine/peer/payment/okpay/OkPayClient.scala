package coinffeine.peer.payment.okpay

import scala.concurrent.{ExecutionContext, Future}

import coinffeine.model.currency.{FiatAmounts, FiatAmount, FiatCurrency}
import coinffeine.model.payment.Payment
import coinffeine.model.payment.PaymentProcessor.{AccountId, Invoice, PaymentId}
import coinffeine.peer.payment.PaymentProcessorException
import coinffeine.peer.payment.okpay.ws.OkPayFault.OkPayFault

sealed trait FeePolicy

object FeePolicy {
  case object PaidByReceiver extends FeePolicy
  case object PaidBySender extends FeePolicy
}

trait OkPayClient {

  val accountId: AccountId

  def sendPayment(
      to: AccountId,
      amount: FiatAmount,
      comment: String,
      invoice: Invoice,
      feePolicy: FeePolicy = FeePolicy.PaidBySender): Future[Payment]

  def findPaymentById(paymentId: PaymentId): Future[Option[Payment]]

  def findPaymentByInvoice(invoice: Invoice): Future[Option[Payment]]

  def currentBalances(): Future[FiatAmounts]

  def currentBalance(currency: FiatCurrency): Future[FiatAmount] = {
    implicit val ec = executionContext
    currentBalances().map { balances =>
      balances.get(currency)
          .getOrElse(throw new PaymentProcessorException(s"No balance in $currency"))
          .asInstanceOf[FiatAmount]
    }
  }

  protected def executionContext: ExecutionContext
}

object OkPayClient {

  abstract class Error(msg: String, cause: Throwable)
      extends Exception(s"invalid OKPay client operation: $msg", cause)

  case class ClientNotFound(account: AccountId, cause: Throwable)
      extends Error(s"account `$account` not found", cause)

  case class AuthenticationFailed(account: AccountId, cause: Throwable)
      extends Error(s"authentication failed for account `$account`", cause)

  case class DisabledCurrency(cause: Throwable)
      extends Error("currency is disabled", cause)

  case class NotEnoughMoney(account: AccountId, cause: Throwable)
      extends Error(s"not enough funds in account $account", cause)

  case class TransactionNotFound(cause: Throwable)
      extends Error("transaction not found", cause)

  case class UnsupportedPaymentMethod(cause: Throwable)
      extends Error("unsupported payment method", cause)

  case class ReceiverNotFound(receiver: AccountId, cause: Throwable)
      extends Error(s"receiver `$receiver` not found", cause)

  case class DuplicatedPayment(receiver: AccountId, invoice: Invoice, cause: Throwable)
      extends Error(s"invoice `$invoice` already exists for receiver `$receiver`", cause)

  case class InternalError(cause: Throwable)
      extends Error("internal error", cause)

  case class UnexpectedError(fault: OkPayFault, cause: Throwable)
      extends Error(s"unexpected $fault", cause)

  case class UnsupportedError(unknownFault: String, cause: Throwable)
      extends Error(s"unsupported fault '$unknownFault' error", cause)

}
