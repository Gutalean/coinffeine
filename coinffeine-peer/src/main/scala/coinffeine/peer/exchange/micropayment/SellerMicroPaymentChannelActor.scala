package coinffeine.peer.exchange.micropayment

import scala.concurrent.Future
import scala.util.{Failure, Try}

import akka.actor._
import akka.pattern._

import coinffeine.model.currency.FiatCurrency
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor._
import coinffeine.peer.exchange.micropayment.SellerMicroPaymentChannelActor.PaymentValidationResult
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.{FinalStep, IntermediateStep}
import coinffeine.peer.exchange.protocol.{ExchangeProtocol, MicroPaymentChannel}
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.peer.payment.PaymentProcessorActor.PaymentFound
import coinffeine.protocol.gateway.MessageForwarder
import coinffeine.protocol.gateway.MessageGateway.{ReceiveMessage, Subscribe}
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.messages.exchange._

/** This actor implements the seller's's side of the exchange. You can find more information about
  * the algorithm at https://github.com/Coinffeine/coinffeine/wiki/Exchange-algorithm
  */
private class SellerMicroPaymentChannelActor[C <: FiatCurrency](
    exchangeProtocol: ExchangeProtocol, constants: ProtocolConstants)
  extends Actor with ActorLogging with Stash with StepTimeout {

  import context.dispatcher

  override def postStop(): Unit = {
    cancelTimeout()
  }

  override def receive: Receive = {
    case init: StartMicroPaymentChannel[C] => new InitializedSellerExchange(init).start()
  }

  private class InitializedSellerExchange(init: StartMicroPaymentChannel[C])
    extends InitializedChannelBehavior(init) {
    import init._
    import constants.exchangePaymentProofTimeout

    val forwarderFactory = new MessageForwarder.Factory(messageGateway, context)

    def start(): Unit = {
      log.info("Exchange {}: seller micropayment channel started", exchange.id)
      new StepBehavior(exchangeProtocol.createMicroPaymentChannel(exchange)).start()
    }

    private class StepBehavior(channel: MicroPaymentChannel[C]) {

      def start(): Unit = {
        channel.currentStep match {
          case _: FinalStep =>
            forwardSignaturesExpectingClose()
            context.become(waitForChannelClosed)

          case intermediateStep: IntermediateStep =>
            forwardSignaturesExpectingPaymentProof()
            reportProgress(
              signatures = channel.currentStep.value,
              payments = channel.currentStep.value - 1
            )
            context.become(waitForPaymentProof(intermediateStep))
        }
      }

      private val waitForChannelClosed: Receive = {
        case MicropaymentChannelClosed(channel.exchange.id) => finishExchange()
      }

      private def forwardSignaturesExpectingPaymentProof(): Unit = {
        forwardSignatures("payment proof") {
          case proof @ PaymentProof(_, _, channel.currentStep.value) => proof
        }
      }

      private def forwardSignaturesExpectingClose(): Unit = {
        forwardSignatures("channel closing") {
          case closed @ MicropaymentChannelClosed(channel.exchange.id) => closed
        }
      }

      private def forwardSignatures[A](expectingHint: String)
                                      (confirmation: PartialFunction[PublicMessage, A]): Unit = {
        log.debug("Exchange {}: forwarding signatures for {} expecting {}",
          exchange.id, channel.currentStep, expectingHint)
        val signatureForwarder = forwarderFactory.forward(
          msg = StepSignatures(
            exchange.id, channel.currentStep.value, channel.signCurrentTransaction),
          destination = exchange.counterpartId,
          retry = MessageForwarder.RetrySettings.continuouslyEvery(
            constants.microPaymentChannelResubmitTimeout)
        )(confirmation)
      }

      private def waitForPaymentProof(step: IntermediateStep): Receive = {
        case PaymentProof(_, paymentId, step.value) =>
          log.debug("Received payment proof with ID {} for step {}", paymentId, step.value)
          validatePayment(step, paymentId).onComplete { tryResult =>
            self ! PaymentValidationResult(tryResult)
          }
          context.become(waitForPaymentValidation(paymentId, step))
        case PaymentProof(_, paymentId, otherStep) =>
          log.debug("Received a payment with ID {} for an unexpected step {}: ignored",
            paymentId, otherStep)
        case MessageForwarder.ConfirmationFailed(_) =>
          val errorMsg = "Timed out waiting for the buyer to provide a valid " +
            s"payment proof ${channel.currentStep}"
          log.warning("Exchange {}: {}", exchange.id, errorMsg)
          finishWith(ExchangeFailure(TimeoutException(errorMsg)))
      }

      private def waitForPaymentValidation(paymentId: String, step: IntermediateStep): Receive = {
        case PaymentValidationResult(Failure(cause)) =>
          unstashAll()
          log.error(cause, "Exchange {}: invalid payment proof received in {}: {}",
            exchange.id, channel.currentStep, paymentId)
          forwardSignaturesExpectingPaymentProof()
          context.become(waitForPaymentProof(step))

        case PaymentValidationResult(_) =>
          unstashAll()
          log.debug("Exchange {}: valid payment proof in {}", exchange.id, channel.currentStep)
          reportProgress(signatures = step.value, payments = step.value)
          new StepBehavior(channel.nextStep).start()

        case _ => stash()
      }

      private def finishWith(result: Any): Unit = {
        resultListeners.foreach { _ ! result }
        context.stop(self)
      }

      private def finishExchange(): Unit = {
        log.info(s"Exchange {}: micropayment channel finished with success", exchange.id)
        finishWith(ExchangeSuccess(None))
      }

      private def validatePayment(step: IntermediateStep, paymentId: String): Future[Unit] = {
        implicit val timeout = PaymentProcessorActor.RequestTimeout
        for {
          PaymentFound(payment) <- paymentProcessor
            .ask(PaymentProcessorActor.FindPayment(paymentId)).mapTo[PaymentFound]
        } yield {
          require(payment.amount == step.select(exchange.amounts).fiatAmount,
            s"Payment $step amount does not match expected amount")
          require(payment.receiverId == exchange.participants.seller.paymentProcessorAccount,
            s"Payment $step is not being sent to the seller")
          require(payment.senderId == exchange.participants.buyer.paymentProcessorAccount,
            s"Payment $step is not coming from the buyer")
          require(payment.description == PaymentDescription(exchange.id, step),
            s"Payment $step does not have the required description")
          require(payment.completed, s"Payment $step is not complete")
        }
      }
    }
  }
}

object SellerMicroPaymentChannelActor {
  private case class PaymentValidationResult(result: Try[Unit])

  def props(exchangeProtocol: ExchangeProtocol, constants: ProtocolConstants) =
    Props(new SellerMicroPaymentChannelActor(exchangeProtocol, constants))
}
