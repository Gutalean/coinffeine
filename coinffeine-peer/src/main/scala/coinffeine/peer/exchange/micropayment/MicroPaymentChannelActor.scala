package coinffeine.peer.exchange.micropayment

import akka.actor.ActorRef

import coinffeine.model.bitcoin._
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.{Both, RunningExchange}

/** A micropayment channel actor is in charge of performing each of the exchange steps by
  * sending/receiving bitcoins and fiat.
  */
object MicroPaymentChannelActor {

  /** Sent to the the actor to start the actual exchange through the micropayment channel.
    *
    * @param exchange          Exchange to take part on
    * @param paymentProcessor  Actor to use for making payments
    * @param registry          Registry to locate services
    * @param resultListeners   These actors will receive the result, [[LastBroadcastableOffer]] and
    *                          [[coinffeine.peer.exchange.ExchangeActor.ExchangeProgress]]
    *                          notifications
    */
  case class StartMicroPaymentChannel[C <: FiatCurrency](
      exchange: RunningExchange[C],
      paymentProcessor: ActorRef,
      registry: ActorRef,
      resultListeners: Set[ActorRef]
  )

  sealed trait ExchangeResult

  /** Sent to the exchange listeners to notify success of the exchange */
  case class ExchangeSuccess(successTransaction: Option[ImmutableTransaction]) extends ExchangeResult

  /** Sent to the exchange listeners to notify of a failure during the exchange */
  case class ExchangeFailure(cause: Throwable) extends ExchangeResult

  /** Sent to the listeners to notify about what the last broadcastable offer is */
  case class LastBroadcastableOffer(transaction: ImmutableTransaction)

  private[micropayment] case object StepSignatureTimeout

  case class TimeoutException(message: String) extends RuntimeException(message)

  case class InvalidStepSignatures(step: Int, sigs: Both[TransactionSignature], cause: Throwable)
    extends RuntimeException(s"Received an invalid step signature for $step. Received: $sigs", cause)
}
