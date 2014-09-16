package coinffeine.peer.exchange

import scala.util.Try

import akka.actor.{ActorRef, Props}

import coinffeine.model.bitcoin._
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._

/** This actor handles all the necessary steps for an exchange to happen */
object ExchangeActor {
  type ExchangeActorProps = (ExchangeToStart[_ <: FiatCurrency], Collaborators) => Props

  val HandshakeActorName = "handshake"
  val ChannelActorName = "exchange"
  val TransactionBroadcastActorName = "transactionBroadcast"

  case class Collaborators(wallet: ActorRef,
                           paymentProcessor: ActorRef,
                           gateway: ActorRef,
                           bitcoinPeer: ActorRef,
                           resultListener: ActorRef)

  case class ExchangeToStart[C <: FiatCurrency](info: NonStartedExchange[C],
                                                user: Exchange.PeerInfo)

  /** This is sent back to listener to indicate exchange progress. */
  case class ExchangeProgress(exchange: RunningExchange[_ <: FiatCurrency])

  sealed trait ExchangeResult

  /** This is a message sent to the listener to indicate that an exchange succeeded */
  case class ExchangeSuccess(exchange: CompletedExchange[_ <: FiatCurrency]) extends ExchangeResult

  /** This is a message sent to the listener to indicate that an exchange failed */
  case class ExchangeFailure(e: Throwable) extends ExchangeResult

  case class InvalidCommitments(validationResult: Both[Try[Unit]])
    extends RuntimeException(s"Commitments were invalid: $validationResult")

  case class CommitmentTxNotInBlockChain(txId: Hash) extends RuntimeException(
    s"Handshake reported that the commitment transaction with hash $txId was in " +
      s"blockchain but it could not be found")

  case class UnexpectedTxBroadcast(effectiveTx: ImmutableTransaction, expectedTx: ImmutableTransaction)
    extends RuntimeException(
      s"""The transaction broadcast for this exchange is different from the one that was being expected.
            |   Sent transaction: $effectiveTx
            |   Expected: $expectedTx""".stripMargin)

  case class TxBroadcastFailed(cause: Throwable) extends RuntimeException(
    "The final transaction could not be broadcast", cause)

  case class RiskOfValidRefund(broadcastTx: ImmutableTransaction) extends RuntimeException(
    "The exchange was forcefully finished because it was taking too long and there was a chance" +
      "that the refund transaction could have become valid"
  )

  trait Component {
    def exchangeActorProps: ExchangeActorProps
  }
}
