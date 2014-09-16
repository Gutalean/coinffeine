package coinffeine.peer.exchange.micropayment

import akka.actor.ActorRef

import coinffeine.model.bitcoin._
import coinffeine.model.exchange.Both

/** A micropayment channel actor is in charge of performing each of the exchange steps by
  * sending/receiving bitcoins and fiat.
  */
object MicroPaymentChannelActor {

  /** Collaborator actors for a micropayment channel
    *
    * @param gateway           Message gateway
    * @param paymentProcessor  Actor to use for making payments
    * @param resultListeners   These actors will receive the result, [[LastBroadcastableOffer]] and
    *                          [[coinffeine.peer.exchange.ExchangeActor.ExchangeUpdate]]
    *                          notifications
    */
  case class Collaborators(gateway: ActorRef,
                           paymentProcessor: ActorRef,
                           resultListeners: Set[ActorRef])

  sealed trait ExchangeResult

  /** Sent to the exchange listeners to notify success of the exchange */
  case class ChannelSuccess(successTransaction: Option[ImmutableTransaction]) extends ExchangeResult

  /** Sent to the exchange listeners to notify of a failure during the exchange */
  case class ChannelFailure(step: Int, cause: Throwable) extends ExchangeResult

  /** Sent to the listeners to notify about what the last broadcastable offer is */
  case class LastBroadcastableOffer(transaction: ImmutableTransaction)

  private[micropayment] case object StepSignatureTimeout

  case class InvalidStepSignatures(step: Int, sigs: Both[TransactionSignature], cause: Throwable)
    extends RuntimeException(s"Received an invalid step signature for $step. Received: $sigs", cause)
}
