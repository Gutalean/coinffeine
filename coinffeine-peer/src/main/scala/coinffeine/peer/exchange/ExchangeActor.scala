package coinffeine.peer.exchange

import akka.actor.{ActorRef, Props}

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._

/** This actor handles all the necessary steps for an exchange to happen */
object ExchangeActor {
  val HandshakeActorName = "handshake"
  val ChannelActorName = "exchange"
  val TransactionBroadcastActorName = "transactionBroadcast"

  case class Collaborators(wallet: ActorRef,
                           paymentProcessor: ActorRef,
                           gateway: ActorRef,
                           bitcoinPeer: ActorRef,
                           blockchain: ActorRef,
                           listener: ActorRef)

  /** This is sent back to listener to indicate exchange progress. */
  case class ExchangeUpdate(exchange: AnyStateExchange[_ <: FiatCurrency])

  sealed trait ExchangeResult

  /** This is a message sent to the listener to indicate that an exchange succeeded */
  case class ExchangeSuccess(exchange: SuccessfulExchange[_ <: FiatCurrency]) extends ExchangeResult

  /** This is a message sent to the listener to indicate that an exchange failed */
  case class ExchangeFailure(exchange: FailedExchange[_ <: FiatCurrency]) extends ExchangeResult

  trait Component {
    def exchangeActorProps(exchange: NonStartedExchange[_ <: FiatCurrency],
                           collaborators: Collaborators): Props
  }
}
