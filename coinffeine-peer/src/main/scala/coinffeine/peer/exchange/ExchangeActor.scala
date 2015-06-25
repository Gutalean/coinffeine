package coinffeine.peer.exchange

import akka.actor.{ActorRef, Props}

import coinffeine.model.exchange._

/** This actor handles all the necessary steps for an exchange to happen */
object ExchangeActor {
  val HandshakeActorName = "handshake"
  val ChannelActorName = "exchange"
  val BroadcasterActorName = "broadcaster"

  case class Collaborators(
      wallet: ActorRef,
      paymentProcessor: ActorRef,
      gateway: ActorRef,
      bitcoinPeer: ActorRef,
      blockchain: ActorRef,
      listener: ActorRef)

  /** This is sent back to listener to indicate exchange progress. */
  case class ExchangeUpdate(exchange: Exchange)

  sealed trait ExchangeResult

  /** This is a message sent to the listener to indicate that an exchange succeeded */
  case class ExchangeSuccess(exchange: SuccessfulExchange) extends ExchangeResult

  /** This is a message sent to the listener to indicate that an exchange failed */
  case class ExchangeFailure(exchange: FailedExchange) extends ExchangeResult

  /** Acknowledge the exchange result and allow the exchange to terminate */
  case object FinishExchange

  trait Component {
    def exchangeActorProps(
        exchange: HandshakingExchange,
        collaborators: Collaborators): Props
  }

}
