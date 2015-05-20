package coinffeine.peer.exchange.micropayment

import akka.actor.ActorRef

import coinffeine.model.bitcoin._

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

  sealed trait ChannelResult

  /** Sent to the exchange listeners to notify success of the exchange */
  case class ChannelSuccess(successTransaction: Option[ImmutableTransaction]) extends ChannelResult

  /** Sent to the exchange listeners to notify of a failure during the exchange */
  case class ChannelFailure(step: Int, cause: Throwable) extends ChannelResult

  /** Sent to the listeners to notify about what the last broadcastable offer is */
  case class LastBroadcastableOffer(transaction: ImmutableTransaction)

  /** Receive when actor must finish and delete its journal. */
  case object Finish

  private[micropayment] case object StepSignatureTimeout
}
