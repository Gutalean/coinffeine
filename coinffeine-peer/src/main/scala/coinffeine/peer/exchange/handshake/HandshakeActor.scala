package coinffeine.peer.exchange.handshake

import coinffeine.model.bitcoin._
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._

/** A handshake actor is in charge of entering into a value exchange by getting a refundSignature
  * transaction signed and relying on the broker to publish the commitment TX.
  */
object HandshakeActor {

  sealed trait HandshakeResult

  /** Sent to the handshake listeners to notify success. */
  case class HandshakeSuccess(exchange: HandshakingExchange[_ <: FiatCurrency],
                              bothCommitments: Both[ImmutableTransaction],
                              refundTx: ImmutableTransaction) extends HandshakeResult

  /** Sent to the handshake listeners to notify a failure without having committed funds. */
  case class HandshakeFailure(cause: Throwable) extends HandshakeResult

  /** Send to listeners to notify a handshake failure after having compromised funds */
  case class HandshakeFailureWithCommitment(exchange: HandshakingExchange[_ <: FiatCurrency],
                                            cause: Throwable,
                                            commitment: ImmutableTransaction,
                                            refundTx: ImmutableTransaction) extends HandshakeResult

  case class RefundSignatureTimeoutException(exchangeId: ExchangeId) extends RuntimeException(
    s"Timeout waiting for a valid signature of the refund transaction of handshake $exchangeId")

  case class CommitmentTransactionRejectedException(
       exchangeId: ExchangeId, rejectedTx: Hash, isOwn: Boolean) extends RuntimeException(
    s"Commitment transaction $rejectedTx (${if (isOwn) "ours" else "counterpart"}) was rejected"
  )

  case class HandshakeAbortedException(exchangeId: ExchangeId, reason: String)
    extends RuntimeException(s"Handshake $exchangeId aborted externally: $reason")
}
