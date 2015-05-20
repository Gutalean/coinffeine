package coinffeine.peer.exchange.broadcast

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.peer.bitcoin.BitcoinPeerActor._

/** This actor is in charge of broadcasting the appropriate transactions for an exchange, whether
  * the exchange ends successfully or not.
  *
  * The actor will receive via props the refund transaction and a listener actor ref. This refund
  * will be broadcast as soon as its timelock expires if there are no better alternatives (like
  * broadcasting the successful exchange transaction).
  */
object TransactionBroadcaster {

  /** A request for the actor to finish the exchange and broadcast the best possible transaction */
  case object PublishBestTransaction

  sealed trait BroadcastResult

  /** A message sent to the listeners indicating that the exchange could be finished by broadcasting
    * a transaction. This message can also be sent once the micropayment actor has been set if the
    * exchange has been forcefully closed due to the risk of having the refund exchange be valid.
    */
  case class SuccessfulBroadcast(publishedTransaction: TransactionPublished) extends BroadcastResult

  /** A message sent to the listeners indicating that the broadcast of the best transaction was not
    * performed due to an error.
    */
  case class FailedBroadcast(cause: Throwable) extends BroadcastResult

  case class UnexpectedTxBroadcast(unexpectedTx: ImmutableTransaction) extends RuntimeException(
    "The exchange finished with a successful broadcast, but the transaction that was published was" +
      s"not the one that was being expected: $unexpectedTx")

  /** Response to an [[BroadcastResult]] to acknowledge completion and terminate the broadcaster */
  case object Finish
}
