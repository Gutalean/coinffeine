package coinffeine.peer.exchange.micropayment

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import akka.actor._
import akka.pattern._
import akka.persistence.RecoveryCompleted

import coinffeine.common.akka.ResubmitTimer.ResubmitTimeout
import coinffeine.common.akka.persistence.PersistentEvent
import coinffeine.common.akka.{AskPattern, ResubmitTimer}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.payment.Payment
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.ExchangeActor.ExchangeUpdate
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.ChannelSuccess
import coinffeine.peer.exchange.protocol.MicroPaymentChannel
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.{FinalStep, IntermediateStep}
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.peer.payment.PaymentProcessorActor._
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.ReceiveMessage
import coinffeine.protocol.messages.exchange.{MicropaymentChannelClosed, PaymentProof, StepSignatures}

class SellerMicroPaymentChannelActor[C <: FiatCurrency](
    constants: ProtocolConstants,
    collaborators: MicroPaymentChannelActor.Collaborators,
    initialChannel: MicroPaymentChannel[C])
  extends BaseChannelActor(initialChannel.exchange, collaborators) with ActorLogging {

  import context.dispatcher
  import SellerMicroPaymentChannelActor._

  private var channel = initialChannel
  private var exchange = initialChannel.exchange.completeStep(1)
  override val persistenceId = "micropayment-channel-" + exchange.id.value
  private val resubmitTimer = new ResubmitTimer(context, constants.microPaymentChannelResubmitTimeout)

  override def preStart(): Unit = {
    super.preStart()
    log.info(s"Exchange {}: seller micropayment channel started", exchange.id)
    subscribeToMessages()
    resubmitTimer.start()
  }

  override def postStop(): Unit = {
    resubmitTimer.cancel()
    super.postStop()
  }

  private def subscribeToMessages(): Unit = {
    val id = initialChannel.exchange.id
    val counterpartId = initialChannel.exchange.counterpartId
    collaborators.gateway ! MessageGateway.Subscribe {
      case ReceiveMessage(
        PaymentProof(`id`, _, _) | MicropaymentChannelClosed(`id`), `counterpartId`) =>
    }
  }

  override val receiveRecover: Receive = {
    case AcceptedPayment => onAcceptedPayment()
    case ChannelClosed => onChannelClosed()
    case RecoveryCompleted =>
      notifyProgress()
      self ! ResubmitTimeout
  }

  override val receiveCommand: Receive = submittingSignatures orElse {
    case ReceiveMessage(PaymentProof(_, paymentId, step), _) if step == channel.currentStep.value =>
      log.info("Exchange {}: received {} for step {}", exchange.id, paymentId, channel.currentStep)
      resubmitTimer.reset()
      lookupPayment(paymentId).pipeTo(self)

    case ReceiveMessage(proof: PaymentProof, _) =>
      log.warning("Exchange {}: received unexpected {}", exchange.id, proof)

    case PaymentFound(payment) =>
      validatePayment(payment) match {
        case Failure(cause) =>
          log.error(cause, "Exchange {}: invalid payment {}", exchange.id, payment.id)

        case Success(_) =>
          log.info("")
          resubmitTimer.reset()
          persist(AcceptedPayment){ _ =>
            onAcceptedPayment()
            self ! ResubmitTimeout
          }
      }

    case PaymentNotFound(paymentId) =>
      log.error("Exchange {}: no payment with id {} found", exchange.id, paymentId)

    case FindPaymentFailed(paymentId, error) =>
      log.error(error, "Exchange {}: cannot look up payment {}", exchange.id, paymentId)
  }

  private val closingChannel: Receive = submittingSignatures orElse {
    case ReceiveMessage(_: MicropaymentChannelClosed, _) =>
      persist(ChannelClosed)(_ => onChannelClosed())
  }

  private def submittingSignatures: Receive = {
    case ResubmitTimeout =>
      val stepSignatures = StepSignatures(
        exchange.id, channel.currentStep.value, channel.signCurrentTransaction)
      forwardToCounterpart(stepSignatures)
  }

  private def onAcceptedPayment(): Unit = {
    channel = channel.nextStep
    exchange = exchange.completeStep(channel.currentStep.value)
    if (recoveryFinished) {
      notifyProgress()
    }
    if (channel.currentStep.isFinal) {
      context.become(closingChannel)
    }
  }

  private def onChannelClosed(): Unit = {
    notifyListeners(ChannelSuccess(None))
    context.stop(self)
  }

  private def lookupPayment(paymentId: String): Future[FindPaymentResponse] = {
    implicit val timeout = PaymentProcessorActor.RequestTimeout
    AskPattern(
      to = collaborators.paymentProcessor,
      request = FindPayment(paymentId),
      errorMessage = s"cannot find payment $paymentId"
    ).withReply[FindPaymentResponse]
  }

  def validatePayment(payment: Payment[_ <: FiatCurrency]): Try[Unit] = Try {
    channel.currentStep match {
      case _: FinalStep => throw new IllegalArgumentException("No payment is expected at the final step")
      case step: IntermediateStep =>
        val participants = channel.exchange.participants
        val expectedDescription = PaymentDescription(channel.exchange.id, step)
        require(payment.amount == step.select(channel.exchange.amounts).fiatAmount,
          s"Payment $step amount does not match expected amount")
        require(payment.receiverId == participants.seller.paymentProcessorAccount,
          s"Payment $step is not being sent to the seller")
        require(payment.senderId == participants.buyer.paymentProcessorAccount,
          s"Payment $step is not coming from the buyer")
        require(payment.description == expectedDescription,
          s"Payment $step description (${payment.description}) does not match expected ($expectedDescription)")
        require(payment.completed, s"Payment $step is not complete")
    }
  }

  private def notifyProgress(): Unit = {
    notifyListeners(ExchangeUpdate(exchange))
  }
}

object SellerMicroPaymentChannelActor {

  def props(initialChannel: MicroPaymentChannel[_ <: FiatCurrency],
            constants: ProtocolConstants,
            collaborators: MicroPaymentChannelActor.Collaborators) =
    Props(new SellerMicroPaymentChannelActor(constants, collaborators, initialChannel))

  private case object AcceptedPayment extends PersistentEvent
  private case object ChannelClosed extends PersistentEvent
}
