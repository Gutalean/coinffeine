package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration._

import akka.actor._
import akka.util.Timeout

import coinffeine.model.currency.FiatCurrency
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.peer.payment.PaymentProcessorActor._
import coinffeine.peer.payment.okpay.OkPayClient.DuplicatedPayment

/** An actor that ensures a payment is made through a payment processor.
  *
  * After receiving a [[PayerActor.EnsurePayment]] message, it starts ensuring that the wrapped
  * payment is made. It will retry and manage the corresponding error until the payment is fully
  * confirmed by the payment processor. Then, [[PayerActor.PaymentEnsured]] is responded back and
  * the PayerActor terminates.
  */
class PayerActor(retryTimeout: Timeout) extends Actor with ActorLogging {

  import PayerActor._

  override def receive = waitingForWork

  private def waitingForWork: Receive = {
    case payment @ EnsurePayment(request, paymentProcessor) =>
      paymentProcessor ! request
      context.setReceiveTimeout(retryTimeout.duration)
      context.become(waitingPaymentResponse(sender(), payment))
  }

  private def waitingPaymentResponse[C <: FiatCurrency](requester: ActorRef,
                                                        payment: EnsurePayment[C]): Receive = {
    case ReceiveTimeout =>
      log.warning("payment processor didn't respond to payment request in {}, retrying...",
        retryTimeout.duration)
      payment.paymentProcessor ! payment.request

    case paid @ PaymentProcessorActor.Paid(_) =>
      log.debug("payment processor responded with success paid, notifying requester...")
      requester ! PaymentEnsured(paid)
      context.stop(self)

    case PaymentProcessorActor.PaymentFailed(_, _: DuplicatedPayment) =>
      log.warning("payment processor responded with duplicated payment error; " +
        "retrieving the actual payment...")
      payment.paymentProcessor ! PaymentProcessorActor.FindPayment(
        FindPaymentCriterion.ByInvoice(payment.request.invoice))
      context.become(retrievingPayment(requester, payment))

    case PaymentProcessorActor.PaymentFailed(_, cause) =>
      log.error(cause, "payment failed; retrying in {}... ",
        retryTimeout.duration)
      context.become(waitingToRetryAfterFailure(requester, payment))
  }

  private def waitingToRetryAfterFailure[C <: FiatCurrency](requester: ActorRef,
                                                            payment: EnsurePayment[C]): Receive = {
    case ReceiveTimeout =>
      log.info("retrying after failure...")
      payment.paymentProcessor ! payment.request
      context.become(waitingPaymentResponse(requester, payment))
  }

  private def retrievingPayment[C <: FiatCurrency](
      requester: ActorRef, payment: EnsurePayment[C]): Receive = {
    case PaymentFound(foundPayment) =>
      requester ! PaymentEnsured(PaymentProcessorActor.Paid(foundPayment))
      context.stop(self)
    case PaymentNotFound(_) =>
      val msg = s"payment not found for invoice ${payment.request.invoice} after duplicated payment error"
      log.error(msg)
      requester ! CannotEnsurePayment(payment.request, new IllegalStateException(msg))
      context.stop(self)
    case FindPaymentFailed(_, cause) =>
      log.error("cannot find payment for invoice {} after duplicated payment error: {}",
        payment.request.invoice, cause)
    case ReceiveTimeout =>
      log.info("timeout while retrieving payment for invoice {}, retrying...",
        payment.request.invoice)
      payment.paymentProcessor ! PaymentProcessorActor.FindPayment(
        FindPaymentCriterion.ByInvoice(payment.request.invoice))
  }

}

object PayerActor {

  val DefaultRetryTimeout = Timeout(30.seconds)

  /** A message sent to the payer actor requesting it to ensure a payment is made. */
  case class EnsurePayment[C <: FiatCurrency](
    request: PaymentProcessorActor.Pay[C],
    paymentProcessor: ActorRef)

  /** A response sent by payer actor after [[EnsurePayment]] indicating the payment was ensured. */
  case class PaymentEnsured[C <: FiatCurrency](response: PaymentProcessorActor.Paid[C])

  /** A response sent by payer actor after [[EnsurePayment]] indicating the payment couldn't be ensured. */
  case class CannotEnsurePayment[C <: FiatCurrency](request: PaymentProcessorActor.Pay[C],
                                                    cause: Throwable)

  def props(retryTimeout: Timeout = DefaultRetryTimeout): Props = Props(new PayerActor(retryTimeout))
}
