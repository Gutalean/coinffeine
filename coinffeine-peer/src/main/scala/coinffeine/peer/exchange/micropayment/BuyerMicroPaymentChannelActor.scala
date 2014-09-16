package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration.Duration
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
import coinffeine.peer.exchange.protocol.MicroPaymentChannel
import coinffeine.peer.exchange.protocol.MicroPaymentChannel._
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.gateway.MessageGateway.{ReceiveMessage, Subscribe}
import coinffeine.protocol.messages.exchange.{MicropaymentChannelClosed, PaymentProof, StepSignatures}

/** This actor implements the buyer's side of the exchange. You can find more information about
  * the algorithm at https://github.com/Coinffeine/coinffeine/wiki/Exchange-algorithm
  */
private class BuyerMicroPaymentChannelActor[C <: FiatCurrency](
    initialChannel: MicroPaymentChannel[C],
    constants: ProtocolConstants,
    collaborators: Collaborators)
  extends BaseChannelActor(initialChannel.exchange, collaborators) with ActorLogging {

  private val exchange = initialChannel.exchange
  private var lastSignedOffer: Option[ImmutableTransaction] = None

  override def preStart(): Unit = {
    subscribeToMessages()
    log.info(s"Exchange {}: buyer micropayment channel started", exchange.id)
  }

  override def receive: Receive = waitForNextStepSignature(initialChannel)

  private def subscribeToMessages(): Unit = {
    val counterpart = exchange.counterpartId
    collaborators.gateway ! Subscribe {
      case ReceiveMessage(StepSignatures(exchange.`id`, _, _), `counterpart`) =>
    }
  }

  private def waitForNextStepSignature(channel: MicroPaymentChannel[C],
                                       previousPaymentProof: Option[PaymentProof] = None): Receive =
    waitForValidSignature(channel, previousPaymentProof) { signatures =>
      updateLastSignedOffer(channel.closingTransaction(signatures))
      channel.currentStep match {
        case step: IntermediateStep =>
          log.debug("Exchange {}: received valid signature at {}, paying", exchange.id, step)
          reportProgress(signatures = step.value, payments = step.value - 1)
          pay(step)
          context.become(waitForPaymentResult(channel))

        case _: FinalStep =>
          log.info("Exchange {}: micropayment channel finished with success", exchange.id)
          reportClosedChannel()
          finishWith(ChannelSuccess(lastSignedOffer))
      }
    }

  private def waitForPaymentResult(channel: MicroPaymentChannel[C]): Receive = {
    case proof: PaymentProof =>
      val completedSteps = channel.currentStep.value
      reportProgress(signatures = completedSteps, payments = completedSteps)
      log.debug("Exchange {}: payment {} for step {} done",
        exchange.id, proof.paymentId, completedSteps)
      forwarding.forwardToCounterpart(proof)
      context.become(waitForNextStepSignature(channel.nextStep, Some(proof)))

    case PaymentProcessorActor.PaymentFailed(_, cause) =>
      // TODO: look more carefully to the error and consider retrying
      finishWith(ChannelFailure(channel.currentStep.value, cause))
  }

  private def waitForValidSignature(channel: MicroPaymentChannel[C],
                                    previousPaymentProof: Option[PaymentProof])
                                   (body: Both[TransactionSignature] => Unit): Receive = {
    val behavior: Receive = {
      case ReceiveMessage(StepSignatures(_, channel.currentStep.`value`, signatures), _) =>
        channel.validateCurrentTransactionSignatures(signatures) match {
          case Success(_) =>
            context.setReceiveTimeout(Duration.Undefined)
            body(signatures)
          case Failure(cause) =>
            log.error(cause, s"Exchange {}: received invalid signature for {}: ({})",
              exchange.id, channel.currentStep, signatures)
            finishWith(ChannelFailure(channel.currentStep.value,
              InvalidStepSignatures(channel.currentStep.value, signatures, cause)))
        }

      case ReceiveTimeout =>
        previousPaymentProof.foreach(forwarding.forwardToCounterpart)
    }
    context.setReceiveTimeout(constants.microPaymentChannelResubmitTimeout)
    behavior
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
    collaborators.resultListeners.foreach { _ ! message }
  }

  private def pay(step: IntermediateStep): Unit = {
    import context.dispatcher
    implicit val timeout = PaymentProcessorActor.RequestTimeout
    val request = PaymentProcessorActor.Pay(
      fundsId = exchange.blockedFunds.fiat.get,
      to = exchange.state.counterpart.paymentProcessorAccount,
      amount = step.select(exchange.amounts).fiatAmount,
      comment = PaymentDescription(exchange.id, step)
    )
    AskPattern(collaborators.paymentProcessor, request, errorMessage = s"Cannot pay at $step")
      .withReplyOrError[PaymentProcessorActor.Paid[C],
                        PaymentProcessorActor.PaymentFailed[C]](_.error)
      .map(paid => PaymentProof(exchange.id, paid.payment.id, step.value))
      .recover { case NonFatal(cause) => PaymentProcessorActor.PaymentFailed(request, cause) }
      .pipeTo(self)
  }

  private def reportClosedChannel(): Unit = {
    forwarding.forwardToCounterpart(MicropaymentChannelClosed(exchange.id))
  }
}

object BuyerMicroPaymentChannelActor {

  def props(initialChannel: MicroPaymentChannel[_ <: FiatCurrency],
            constants: ProtocolConstants,
            collaborators: Collaborators) =
    Props(new BuyerMicroPaymentChannelActor(initialChannel, constants, collaborators))
}
