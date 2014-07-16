package coinffeine.peer.exchange.micropayment

import scala.concurrent.Future
import scala.util.{Failure, Success}

import akka.actor._
import akka.pattern._

import coinffeine.model.bitcoin._
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Both
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor._
import coinffeine.peer.exchange.protocol.MicroPaymentChannel._
import coinffeine.peer.exchange.protocol.{ExchangeProtocol, MicroPaymentChannel}
import coinffeine.peer.exchange.util.MessageForwarding
import coinffeine.peer.payment.PaymentProcessor
import coinffeine.peer.payment.PaymentProcessor.Paid
import coinffeine.protocol.gateway.MessageGateway.{ReceiveMessage, Subscribe}
import coinffeine.protocol.messages.exchange.{PaymentProof, StepSignatures}

/** This actor implements the buyer's side of the exchange. You can find more information about
  * the algorithm at https://github.com/Coinffeine/coinffeine/wiki/Exchange-algorithm
  */
class BuyerMicroPaymentChannelActor[C <: FiatCurrency](exchangeProtocol: ExchangeProtocol)
  extends Actor with ActorLogging with StepTimeout  {

  override def postStop(): Unit = {
    cancelTimeout()
  }

  override def receive: Receive = {
    case init: StartMicroPaymentChannel[C] =>
      new InitializedBuyer(init).startExchange()
  }

  private class InitializedBuyer(init: StartMicroPaymentChannel[C]) {
    import init._
    import init.constants.exchangeSignatureTimeout

    private val forwarding =
      new MessageForwarding(messageGateway, exchange, exchange.role)
    private var lastSignedOffer: Option[ImmutableTransaction] = None

    def startExchange(): Unit = {
      subscribeToMessages()
      val channel = exchangeProtocol.createMicroPaymentChannel(exchange)
      context.become(waitForNextStepSignature(channel) orElse handleLastOfferQueries)
      log.info(s"Exchange ${exchange.id}: Exchange started")
    }

    private def subscribeToMessages(): Unit = {
      val counterpart = exchange.role.counterpart.select(exchange.peerIds)
      messageGateway ! Subscribe {
        case ReceiveMessage(StepSignatures(exchange.`id`, _, _), `counterpart`) => true
        case _ => false
      }
    }

    private val handleLastOfferQueries: Receive = {
      case GetLastOffer => sender ! LastOffer(lastSignedOffer)
    }

    private def withStepTimeout(channel: MicroPaymentChannel[C])(receive: Receive): Receive = {
      scheduleStepTimeouts(exchangeSignatureTimeout)
      receive.andThen(_ => cancelTimeout()).orElse(handleTimeout(channel.currentStep))
    }

    private def handleTimeout(step: Step): Receive= {
      case StepSignatureTimeout =>
        val errorMsg = s"Timed out waiting for the seller to provide the signature for $step" +
          s" (out of ${exchange.amounts.breakdown.intermediateSteps}})"
        log.warning(errorMsg)
        finishWith(ExchangeFailure(TimeoutException(errorMsg)))
    }

    private def waitForNextStepSignature(channel: MicroPaymentChannel[C]): Receive =
      withStepTimeout(channel) {
        waitForValidSignature(channel) { signatures =>
          lastSignedOffer = Some(channel.closingTransaction(signatures))
          channel.currentStep match {
            case step: IntermediateStep =>
              forwarding.forwardToCounterpart(pay(step))
              context.become(waitForNextStepSignature(channel.nextStep) orElse handleLastOfferQueries)
            case _: FinalStep =>
              log.info(s"Exchange ${exchange.id}: exchange finished with success")
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
            log.warning(s"Received invalid signature for ${channel.currentStep}: " +
              s"($signatures). Reason: $cause")
            finishWith(ExchangeFailure(
              InvalidStepSignatures(channel.currentStep.value, signatures, cause)))
        }
    }

    private def finishWith(result: Any): Unit = {
      resultListeners.foreach { _ ! result }
      context.become(handleLastOfferQueries)
    }

    private def pay(step: IntermediateStep): Future[PaymentProof] = {
      import context.dispatcher
      implicit val timeout = PaymentProcessor.RequestTimeout

      val paymentRequest = PaymentProcessor.Pay(
        exchange.counterpart.paymentProcessorAccount,
        exchange.amounts.stepFiatAmount,
        PaymentDescription(exchange.id, step)
      )
      for {
        Paid(payment) <- paymentProcessor.ask(paymentRequest).mapTo[Paid[C]]
      } yield PaymentProof(exchange.id, payment.id)
    }
  }
}

object BuyerMicroPaymentChannelActor {
  trait Component { this: ProtocolConstants.Component =>
    def micropaymentChannelActorProps[C <: FiatCurrency]: Props =
      Props[BuyerMicroPaymentChannelActor[C]]
  }
}
