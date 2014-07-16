package coinffeine.peer.exchange.micropayment

import akka.actor.ActorRef

import coinffeine.model.bitcoin._
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.{Both, RunningExchange}
import coinffeine.peer.ProtocolConstants

/** A micropayment channel actor is in charge of performing each of the exchange steps by
  * sending/receiving bitcoins and fiat.
  */
object MicroPaymentChannelActor {

  /** Sent to the the actor to start the actual exchange through the micropayment channel. */
  case class StartMicroPaymentChannel[C <: FiatCurrency](
      exchange: RunningExchange[C],
      constants: ProtocolConstants,
      paymentProcessor: ActorRef,
      messageGateway: ActorRef,
      resultListeners: Set[ActorRef]
  )

  /** Sent to the exchange listeners to notify success of the exchange */
  case class ExchangeSuccess(successTransaction: Option[ImmutableTransaction])

  /** Sent to the exchange listeners to notify of a failure during the exchange */
  case class ExchangeFailure(cause: Throwable)

  /** Sent to the actor to query what the last broadcastable offer is */
  case object GetLastOffer

  /** Sent by the actor as a reply to a GetLastOffer message */
  case class LastOffer(lastOffer: Option[ImmutableTransaction])

  private[micropayment] case object StepSignatureTimeout

  case class TimeoutException(message: String) extends RuntimeException(message)

  case class InvalidStepSignatures(step: Int, sigs: Both[TransactionSignature], cause: Throwable)
    extends RuntimeException(s"Received an invalid step signature for $step. Received: $sigs", cause)
}
