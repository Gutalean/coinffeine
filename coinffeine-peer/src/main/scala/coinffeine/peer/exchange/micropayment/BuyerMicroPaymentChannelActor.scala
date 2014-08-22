package coinffeine.peer.exchange.micropayment

import scala.util.control.NonFatal
import scala.util.{Failure, Success}

import akka.actor._
import akka.pattern._

import coinffeine.common.akka.AskPattern
import coinffeine.model.bitcoin._
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Both
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor._
import coinffeine.peer.exchange.protocol.MicroPaymentChannel._
import coinffeine.peer.exchange.protocol.{ExchangeProtocol, MicroPaymentChannel}
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.gateway.MessageGateway.{ReceiveMessage, Subscribe}
import coinffeine.protocol.messages.exchange.{PaymentProof, StepSignatures}

/** This actor implements the buyer's side of the exchange. You can find more information about
  * the algorithm at https://github.com/Coinffeine/coinffeine/wiki/Exchange-algorithm
  */
private class BuyerMicroPaymentChannelActor[C <: FiatCurrency](
    exchangeProtocol: ExchangeProtocol, constants: ProtocolConstants)
  extends Actor with ActorLogging with StepTimeout {

  override def postStop(): Unit = {
    cancelTimeout()
  }

  override def receive: Receive = {
    case init: StartMicroPaymentChannel[C] => new InitializedBuyer(init).startExchange()
  }

  private class InitializedBuyer(init: StartMicroPaymentChannel[C])
    extends InitializedChannelBehavior(init)(context.dispatcher) {

    import constants.exchangeSignatureTimeout
    import init._

    private var lastSignedOffer: Option[ImmutableTransaction] = None

    def startExchange(): Unit = {
      subscribeToMessages()
      val channel = exchangeProtocol.createMicroPaymentChannel(exchange)
      context.become(waitForNextStepSignature(channel))
      log.info(s"Exchange {}: buyer micropayment channel started", exchange.id)
    }

    private def subscribeToMessages(): Unit = {
      val counterpart = exchange.counterpartId
      messageGateway ! Subscribe {
        case ReceiveMessage(StepSignatures(exchange.`id`, _, _), `counterpart`) =>
      }
    }

    private def withStepTimeout(channel: MicroPaymentChannel[C])(receive: Receive): Receive = {
      cancelTimeout()
      scheduleStepTimeout(exchangeSignatureTimeout)
      receive.orElse(handleTimeout(channel.currentStep))
    }

    private def handleTimeout(step: Step): Receive = {
      case StepSignatureTimeout =>
        val errorMsg = s"Timed out waiting for the seller to provide the signature for $step" +
          s" (out of ${exchange.amounts.breakdown.intermediateSteps}})"
        log.warning(errorMsg)
        finishWith(ExchangeFailure(TimeoutException(errorMsg)))
    }

    private def waitForNextStepSignature(channel: MicroPaymentChannel[C]): Receive =
      withStepTimeout(channel) {
        waitForValidSignature(channel) { signatures =>
          updateLastSignedOffer(channel.closingTransaction(signatures))
          channel.currentStep match {
            case step: IntermediateStep =>
              log.debug("Exchange {}: received valid signature at {}, paying", exchange.id, step)
              reportProgress(signatures = step.value, payments = step.value - 1)
              pay(step)
              context.become(waitForNextStepSignature(channel.nextStep))

            case _: FinalStep =>
              log.info("Exchange {}: micropayment channel finished with success", exchange.id)
              finishWith(ExchangeSuccess(lastSignedOffer))
          }
        }
      }

    private def waitForValidSignature(channel: MicroPaymentChannel[C])
                                     (body: Both[TransactionSignature] => Unit): Receive = {
      case ReceiveMessage(StepSignatures(_, channel.currentStep.`value`, signatures), _) =>
        channel.validateCurrentTransactionSignatures(signatures) match {
          case Success(_) =>
            body(signatures)
          case Failure(cause) =>
            log.error(cause, s"Exchange {}: received invalid signature for {}: ({})",
              exchange.id, channel.currentStep, signatures)
            finishWith(ExchangeFailure(
              InvalidStepSignatures(channel.currentStep.value, signatures, cause)))
        }

      case proof: PaymentProof =>
        val completedSteps = channel.currentStep.value - 1
        reportProgress(signatures = completedSteps, payments = completedSteps)
        log.debug("Exchange {}: payment {} done", exchange.id, completedSteps)
        forwarding.forwardToCounterpart(proof)

      case PaymentProcessorActor.PaymentFailed(_, cause) =>
        finishWith(ExchangeFailure(cause))
    }

    private def finishWith(result: ExchangeResult): Unit = {
      notifyListeners(result)
      self ! PoisonPill
    }

    private def updateLastSignedOffer(newSignedOffer: ImmutableTransaction): Unit = {
      lastSignedOffer = Some(newSignedOffer)
      notifyListeners(LastBroadcastableOffer(newSignedOffer))
    }

    private def notifyListeners(message: Any): Unit = {
      resultListeners.foreach { _ ! message }
    }

    private def pay(step: IntermediateStep): Unit = {
      import context.dispatcher
      implicit val timeout = PaymentProcessorActor.RequestTimeout
      val request = PaymentProcessorActor.Pay(
        fundsId = exchange.blockedFunds.fiat.get,
        to = exchange.state.counterpart.paymentProcessorAccount,
        amount = exchange.amounts.stepFiatAmount,
        comment = PaymentDescription(exchange.id, step)
      )
      AskPattern(paymentProcessor, request, errorMessage = s"Cannot pay at $step")
        .withReplyOrError[PaymentProcessorActor.Paid[C],
                          PaymentProcessorActor.PaymentFailed[C]](_.error)
        .map(paid => PaymentProof(exchange.id, paid.payment.id))
        .recover { case NonFatal(cause) => PaymentProcessorActor.PaymentFailed(request, cause) }
        .pipeTo(self)
    }
  }
}

object BuyerMicroPaymentChannelActor {

  def props(exchangeProtocol: ExchangeProtocol, constants: ProtocolConstants) =
    Props(new BuyerMicroPaymentChannelActor(exchangeProtocol, constants))
}
