package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

import akka.actor._
import akka.pattern._

import coinffeine.common.akka.AskPattern
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
private class BuyerMicroPaymentChannelActor[C <: FiatCurrency](
    initialChannel: MicroPaymentChannel[C],
    constants: ProtocolConstants,
    collaborators: Collaborators)
  extends BaseChannelActor(initialChannel.exchange, collaborators) with ActorLogging {

  private val exchange = initialChannel.exchange
  private val buyer = new BuyerChannel(initialChannel)

  override def preStart(): Unit = {
    subscribeToMessages()
    log.info(s"Exchange {}: buyer micropayment channel started", exchange.id)
    super.preStart()
  }

  private def subscribeToMessages(): Unit = {
    val counterpart = exchange.counterpartId
    collaborators.gateway ! Subscribe {
      case ReceiveMessage(StepSignatures(exchange.`id`, _, _), `counterpart`) =>
    }
  }

  override def receiveRecover: Receive = Map.empty

  override def receiveCommand: Receive = waitingForSignatures

  private def waitingForSignatures: Receive = {
    val behavior: Receive = {
      case ReceiveMessage(stepSignatures: StepSignatures, _) if buyer.shouldAcceptSignatures(stepSignatures) =>
        context.setReceiveTimeout(Duration.Undefined)
        buyer.acceptSignatures(stepSignatures.signatures)
        notifyProgress()
        buyer.paymentRequest match {
          case None =>
            log.info("Exchange {}: micropayment channel finished with success", exchange.id)
            forwardClosedChannel()
            completeWith(ChannelSuccess(buyer.lastOffer))

          case Some(request) =>
            pay(request)
            context.become(paying)
        }

      case ReceiveTimeout => forwardLastPaymentProof()
    }
    context.setReceiveTimeout(constants.microPaymentChannelResubmitTimeout)
    behavior
  }

  private def paying: Receive = {
    case paymentId: String =>
      log.debug("Exchange {}: payment {} for {} done", exchange.id, paymentId, buyer.currentStep)
      buyer.completePayment(paymentId)
      forwardLastPaymentProof()
      context.become(waitingForSignatures)

    case PaymentProcessorActor.PaymentFailed(_, cause) =>
      // TODO: look more carefully to the error and consider retrying
      completeWith(ChannelFailure(buyer.currentStep.value, cause))
  }

  private def completed: Receive = {
    case ReceiveMessage(_: StepSignatures, _) => forwardClosedChannel()
  }

  private def notifyProgress(): Unit = {
    notifyListeners(LastBroadcastableOffer(buyer.lastOffer.get))
    notifyCompletedStep(buyer.currentStep)
  }

  private def pay(request: PaymentProcessorActor.Pay[C]): Unit = {
    import context.dispatcher
    implicit val timeout = PaymentProcessorActor.RequestTimeout
    AskPattern(collaborators.paymentProcessor, request, errorMessage = s"Cannot pay with $request")
      .withReplyOrError[PaymentProcessorActor.Paid[C],
                        PaymentProcessorActor.PaymentFailed[C]](_.error)
      .map(_.payment.id)
      .recover { case NonFatal(cause) => PaymentProcessorActor.PaymentFailed(request, cause) }
      .pipeTo(self)
  }

  private def completeWith(result: ExchangeResult): Unit = {
    notifyListeners(result)
    forwardClosedChannel()
    context.become(completed)
  }

  private def forwardLastPaymentProof(): Unit = {
    buyer.lastPaymentProof.foreach(forwarding.forwardToCounterpart)
  }

  private def forwardClosedChannel(): Unit = {
    forwarding.forwardToCounterpart(MicropaymentChannelClosed(exchange.id))
  }
}

object BuyerMicroPaymentChannelActor {

  def props(initialChannel: MicroPaymentChannel[_ <: FiatCurrency],
            constants: ProtocolConstants,
            collaborators: Collaborators) =
    Props(new BuyerMicroPaymentChannelActor(initialChannel, constants, collaborators))
}
