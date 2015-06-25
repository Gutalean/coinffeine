package coinffeine.peer.payment

import scala.concurrent.duration._

import akka.util.Timeout

import coinffeine.model.currency.{CurrencyAmount, FiatAmount, FiatCurrency}
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.payment.Payment
import coinffeine.model.payment.PaymentProcessor._

object PaymentProcessorActor {

  /** A message sent to the payment processor to reserve some funds for an exchange.
    * Availability will be notified via [[AvailableFunds]] and [[UnavailableFunds]] messages on the
    * event stream.
    */
  case class BlockFunds(id: ExchangeId, amount: FiatAmount)

  /** Reply to [[BlockFunds]] to confirm the funds blocking is created. However, this doesn't mean
    * that the funds are available, that should be notified via [[AvailableFunds]]. */
  case class BlockedFunds(id: ExchangeId)

  /** Funds previously exist so the request had no effect */
  case class AlreadyBlockedFunds(id: ExchangeId)

  /** A message sent to the payment processor to release some previously reserved funds. */
  case class UnblockFunds(id: ExchangeId)

  sealed trait FundsAvailabilityEvent

  /** A message sent by the payment processor to notify that some blocked funds are not available
    * for external reasons.
    */
  case class UnavailableFunds(id: ExchangeId) extends FundsAvailabilityEvent

  /** A message sent by the payment processor to notify when some blocked but not available funds
    * are available back */
  case class AvailableFunds(id: ExchangeId) extends FundsAvailabilityEvent

  /** A message sent to the payment processor ordering a new pay.
    *
    * Funds to transfer should have been previously blocked using [[BlockFunds]] and can be
    * spent partially.
    *
    * This will be responded with either [[Paid]] or [[PaymentFailed]].
    *
    * @param fundsId   Id of the blocked funds to spent
    * @param to        The ID of the receiver account
    * @param amount    The amount of fiat currency to pay
    * @param comment   The comment to be attached to the payment
    */
  case class Pay(
      fundsId: ExchangeId,
      to: AccountId,
      amount: FiatAmount,
      comment: String,
      invoice: Invoice)

  /** A message sent by the payment processor in order to notify of a successful payment. */
  case class Paid(payment: Payment)

  /** A message sent by the payment processor to notify a payment failure.
    *
    * @param request The original pay message that cannot be processed.
    * @param error The error that prevented the request to be processed
    */
  case class PaymentFailed(request: Pay, error: Throwable)

  /** A criterion to find a payment. */
  sealed trait FindPaymentCriterion

  object FindPaymentCriterion {

    case class ById(id: PaymentId) extends FindPaymentCriterion

    case class ByInvoice(invoice: Invoice) extends FindPaymentCriterion

  }

  /** A message sent to the payment processor in order to find a payment. */
  case class FindPayment(criterion: FindPaymentCriterion)

  sealed trait FindPaymentResponse

  /** A message sent by the payment processor to notify a found payment. */
  case class PaymentFound(payment: Payment) extends FindPaymentResponse

  /** A message sent by the payment processor to notify a not found payment. */
  case class PaymentNotFound(criterion: FindPaymentCriterion) extends FindPaymentResponse

  /** A message sent by the payment processor to notify an error while finding a payment. */
  case class FindPaymentFailed(
      criterion: FindPaymentCriterion,
      error: Throwable) extends FindPaymentResponse

  /** A message sent to the payment processor to retrieve the current balance
    * in the given currency.
    * */
  case class RetrieveBalance(currency: FiatCurrency)

  sealed trait RetrieveBalanceResponse

  /** A message sent by the payment processor reporting the current balance in the
    * given currency.
    * */
  case class BalanceRetrieved(
      balance: FiatAmount,
      blockedFunds: FiatAmount) extends RetrieveBalanceResponse

  /** A message sent by the payment processor reporting that the current balance in the
    * given currency cannot be retrieved.
    */
  case class BalanceRetrievalFailed(currency: FiatCurrency, error: Throwable)
      extends RetrieveBalanceResponse

  /** Payment processor requests should be considered to have failed after this period */
  val RequestTimeout = Timeout(5.seconds)
}
