package coinffeine.peer.exchange.micropayment

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import akka.actor._
import akka.pattern._

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.payment.Payment
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor._
import coinffeine.peer.exchange.protocol.MicroPaymentChannel
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.peer.payment.PaymentProcessorActor.PaymentFound
import coinffeine.protocol.gateway.MessageForwarder
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.messages.exchange._

/** This actor implements the seller's side of the exchange. You can find more information about
  * the algorithm at https://github.com/Coinffeine/coinffeine/wiki/Exchange-algorithm
  */
private class SellerMicroPaymentChannelActor[C <: FiatCurrency](
    initialChannel: MicroPaymentChannel[C],
    constants: ProtocolConstants,
    collaborators: Collaborators)
  extends BaseChannelActor(initialChannel.exchange, collaborators) with ActorLogging {

  import context.dispatcher

  private val exchange = initialChannel.exchange
  private val forwarderFactory = new MessageForwarder.Factory(collaborators.gateway, context)
  private val seller = new SellerChannel(initialChannel)

  override def preStart(): Unit = {
    log.info("Exchange {}: seller micropayment channel started", exchange.id)
    super.preStart()
  }

  override def receiveRecover: Receive = Map.empty
  override def receiveCommand: Receive = nextStep()

  private def nextStep(): Receive = {
    notifyCompletedStep(seller.step)
    if (seller.step.isFinal) {
      forwardSignaturesExpectingClose()
      waitForChannelClosed
    } else {
      forwardSignaturesExpectingPaymentProof()
      waitForPaymentProof
    }
  }

  private val waitForChannelClosed: Receive = {
    case MicropaymentChannelClosed(exchange.`id`) => finishExchange()
  }

  private def forwardSignaturesExpectingPaymentProof(): Unit = {
    val step = seller.step.value
    forwardSignatures("payment proof") {
      case proof @ PaymentProof(_, _, `step`) => proof
    }
  }

  private def forwardSignaturesExpectingClose(): Unit = {
    forwardSignatures("channel closing") {
      case closed @ MicropaymentChannelClosed(exchange.`id`) => closed
    }
  }

  private def forwardSignatures[A](expectingHint: String)
                                  (confirmation: PartialFunction[PublicMessage, A]): Unit = {
    log.error("Exchange {}: forwarding signatures for {} expecting {}",
      exchange.id, seller.step, expectingHint)
    forwarderFactory.forward(
      msg = seller.stepSignatures,
      destination = exchange.counterpartId,
      retry = MessageForwarder.RetrySettings.continuouslyEvery(
        constants.microPaymentChannelResubmitTimeout)
    )(confirmation)
  }

  private def waitForPaymentProof: Receive = {
    case proof: PaymentProof if seller.relevantPaymentProof(proof)  =>
      log.info("Received {} for step {}", proof, seller.step)
      findPayment(proof.paymentId)
      context.become(waitForPaymentValidation(proof.paymentId))

    case unexpectedProof: PaymentProof =>
      log.warning("Received unexpected payment proof: {}", unexpectedProof)
  }

  private def waitForPaymentValidation(paymentId: String): Receive = {

    case payment @ Payment(_, _, _, _, _, _, _) =>
      seller.validatePayment(payment) match {
        case Success(_) =>
          log.info("Exchange {}: valid payment proof in {}", exchange.id, seller.step)
          seller.stepPayed()
          context.become(nextStep())

        case Failure(cause) =>
          log.error(cause, "Exchange {}: invalid payment proof received in {}: {}",
            exchange.id, seller.step, paymentId)
          forwardSignaturesExpectingPaymentProof()
          context.become(waitForPaymentProof)
      }

    case Status.Failure(cause) =>
      log.error(cause, "Exchange {}: couldn't find payment {} for {}",
        exchange.id, paymentId, seller.step)
      forwardSignaturesExpectingPaymentProof()
      context.become(waitForPaymentProof)
  }

  private def finishWith(result: Any): Unit = {
    collaborators.resultListeners.foreach { _ ! result }
    context.stop(self)
  }

  private def finishExchange(): Unit = {
    log.error(s"Exchange {}: micropayment channel finished with success", exchange.id)
    finishWith(ChannelSuccess(None))
  }

  private def findPayment(paymentId: String): Future[Payment[_ <: FiatCurrency]] = {
    implicit val timeout = PaymentProcessorActor.RequestTimeout
    (for {
      PaymentFound(payment) <- collaborators.paymentProcessor
        .ask(PaymentProcessorActor.FindPayment(paymentId)).mapTo[PaymentFound]
    } yield payment).pipeTo(self)
  }
}

object SellerMicroPaymentChannelActor {
  private case class PaymentValidationResult(result: Try[Unit])

  def props(initialChannel: MicroPaymentChannel[_ <: FiatCurrency],
            constants: ProtocolConstants,
            collaborators: Collaborators) =
    Props(new SellerMicroPaymentChannelActor(initialChannel, constants, collaborators))
}
