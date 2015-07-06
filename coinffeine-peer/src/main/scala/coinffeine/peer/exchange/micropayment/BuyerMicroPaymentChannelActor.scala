package coinffeine.peer.exchange.micropayment

import akka.actor._
import akka.persistence.RecoveryCompleted
import org.bitcoinj.crypto.TransactionSignature

import coinffeine.common.akka.ResubmitTimer
import coinffeine.common.akka.ResubmitTimer.ResubmitTimeout
import coinffeine.common.akka.persistence.PersistentEvent
import coinffeine.model.Both
import coinffeine.model.currency.FiatCurrency
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor._
import coinffeine.peer.exchange.protocol.MicroPaymentChannel
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.gateway.MessageGateway.{ReceiveMessage, Subscribe}
import coinffeine.protocol.messages.exchange.{MicropaymentChannelClosed, StepSignatures}

/** This actor implements the buyer's side of the exchange. You can find more information about
  * the algorithm at https://github.com/Coinffeine/coinffeine/wiki/Exchange-algorithm
  */
private class BuyerMicroPaymentChannelActor(
    initialChannel: MicroPaymentChannel,
    constants: ProtocolConstants,
    collaborators: Collaborators,
    delegates: BuyerMicroPaymentChannelActor.Delegates)
  extends BaseChannelActor(initialChannel.exchange, collaborators) with ActorLogging {

  import BuyerMicroPaymentChannelActor._

  private case object ResumeMicroPaymentChannel

  private val exchange = initialChannel.exchange
  override def persistenceId: String = s"micropayment-channel-${exchange.id.value}"
  private val buyer = new BuyerChannel(initialChannel)
  private var waitingForPaymentResult = false
  private val resubmitTimer = new ResubmitTimer(context, constants.microPaymentChannelResubmitTimeout)

  override def preStart(): Unit = {
    subscribeToMessages()
    log.info(s"Exchange {}: buyer micropayment channel started", exchange.id)
    resubmitTimer.start()
    super.preStart()
  }

  private def subscribeToMessages(): Unit = {
    val counterpart = exchange.counterpartId
    val id = exchange.id
    collaborators.gateway ! Subscribe {
      case ReceiveMessage(StepSignatures(`id`, _, _), `counterpart`) =>
    }
  }

  override def receiveRecover: Receive = {
    case event: AcceptedSignatures => onAcceptedSignatures(event)
    case event: CompletedPayment => onCompletedPayment(event)
    case event: PaymentFailed => onPaymentFailed(event)
    case RecoveryCompleted =>
      notifyProgress()
      self ! ResumeMicroPaymentChannel
  }

  override def receiveCommand: Receive = waitingForSignatures

  private def waitingForSignatures: Receive = {
    case ReceiveMessage(stepSignatures: StepSignatures, _)
        if buyer.shouldAcceptSignatures(stepSignatures) =>
      resubmitTimer.reset()
      persist(AcceptedSignatures(stepSignatures.signatures))(onAcceptedSignatures)

    case ResubmitTimeout => forwardLastPaymentProof()
  }

  private def onAcceptedSignatures(event: AcceptedSignatures): Unit = {
    buyer.acceptSignatures(event.signatures)
    if (recoveryFinished) {
      notifyProgress()
    }
    buyer.paymentRequest match {
      case None =>
        log.info("Exchange {}: micropayment channel finished with success", exchange.id)
        if (recoveryFinished) {
          forwardClosedChannel()
        }
        completeWith(ChannelSuccess(buyer.lastOffer))

      case Some(request) =>
        if (recoveryFinished) {
          pay(request)
        }
        context.become(paying)
    }
  }

  private def paying: Receive = {
    case ResumeMicroPaymentChannel if !waitingForPaymentResult =>
      pay(buyer.paymentRequest.get)

    case PayerActor.PaymentEnsured(paid) =>
      waitingForPaymentResult = false
      persist(CompletedPayment(paid.payment.paymentId)) { event =>
        log.debug("Exchange {}: payment {} for {} done", exchange.id, paid.payment.paymentId, buyer.currentStep)
        onCompletedPayment(event)
        resubmitTimer.reset()
        forwardLastPaymentProof()
      }

    case PayerActor.CannotEnsurePayment(_, cause) =>
      waitingForPaymentResult = false
      persist(PaymentFailed(cause))(onPaymentFailed)
  }

  private def onCompletedPayment(event: CompletedPayment): Unit = {
    buyer.completePayment(event.paymentId)
    context.become(waitingForSignatures)
  }

  private def onPaymentFailed(event: PaymentFailed): Unit = {
    completeWith(ChannelFailure(buyer.currentStep.value, event.cause))
  }

  private def completeWith(result: ChannelResult): Unit = {
    if (recoveryFinished) {
      notifyListeners(result)
      forwardClosedChannel()
    }
    context.become(finishing(result))
  }

  private def finishing(result: ChannelResult): Receive = {
    case ResumeMicroPaymentChannel =>
      notifyListeners(result)
      forwardClosedChannel()
    case ReceiveMessage(_: StepSignatures, _) => forwardClosedChannel()
    case MicroPaymentChannelActor.Finish =>
      log.debug("Deleting journal after finish request")
      deleteMessages(Long.MaxValue)
  }

  private def notifyProgress(): Unit = {
    buyer.lastOffer.foreach(offer => notifyListeners(LastBroadcastableOffer(offer)))
    buyer.lastCompletedStep.foreach(notifyCompletedStep)
  }

  private def pay(request: PaymentProcessorActor.Pay): Unit = {
    val payer = context.actorOf(delegates.payer())
    payer ! PayerActor.EnsurePayment(request, collaborators.paymentProcessor)
    waitingForPaymentResult = true
  }

  private def forwardLastPaymentProof(): Unit = {
    buyer.lastPaymentProof.foreach(forwardToCounterpart)
  }

  private def forwardClosedChannel(): Unit = {
    forwardToCounterpart(MicropaymentChannelClosed(exchange.id))
  }
}

object BuyerMicroPaymentChannelActor {

  trait Delegates {
    def payer(): Props
  }

  def props(initialChannel: MicroPaymentChannel,
            constants: ProtocolConstants,
            collaborators: Collaborators,
            delegates: Delegates) =
    Props(new BuyerMicroPaymentChannelActor(initialChannel, constants, collaborators, delegates))

  private case class AcceptedSignatures(signatures: Both[TransactionSignature]) extends PersistentEvent
  private case class CompletedPayment(paymentId: String) extends PersistentEvent
  private case class PaymentFailed(cause: Throwable) extends PersistentEvent
}
