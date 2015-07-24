package coinffeine.peer.exchange.broadcast

import akka.actor._
import akka.persistence.{PersistentActor, RecoveryCompleted}

import coinffeine.common.akka.persistence.PersistentEvent
import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BitcoinPeerActor._
import coinffeine.peer.bitcoin.blockchain.BlockchainActor
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.LastBroadcastableOffer

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

  case class UnexpectedTxBroadcast(unexpectedTx: ImmutableTransaction) extends RuntimeException(
    "The exchange finished with a successful broadcast, but the transaction that was published was" +
      s"not the one that was being expected: $unexpectedTx")

  /** Instruct the broadcaster to terminate itself */
  case object Finish

  case class Collaborators(bitcoinPeer: ActorRef, blockchain: ActorRef)

  def props(
    refund: ImmutableTransaction, collaborators: Collaborators, constants: ProtocolConstants) =
    Props(new TransactionBroadcaster(refund, collaborators, constants))

  private case class OfferAdded(offer: ImmutableTransaction) extends PersistentEvent
  private case object PublicationRequested extends PersistentEvent

  /** Unused but kept to maintain the binary compatibility, remove after 0.12 */
  @deprecated private case class FinishedWithResult(result: BroadcastResult)
      extends PersistentEvent
  @deprecated sealed trait BroadcastResult
  @deprecated case class SuccessfulBroadcast(publishedTransaction: TransactionPublished) extends BroadcastResult
  @deprecated case class FailedBroadcast(cause: Throwable) extends BroadcastResult
}

private class TransactionBroadcaster(
  refund: ImmutableTransaction,
  collaborators: TransactionBroadcaster.Collaborators,
  constants: ProtocolConstants) extends PersistentActor with ActorLogging {
  import TransactionBroadcaster._

  override val persistenceId = "broadcast-with-refund-" + refund.get.getHashAsString
  private val policy = new BroadcastPolicy(refund, constants.refundSafetyBlockCount)

  override def preStart(): Unit = {
    watchRelevantBlocks()
    super.preStart()
  }

  private def watchRelevantBlocks(): Unit = {
    for (blockHeight <- policy.relevantBlocks) {
      collaborators.blockchain ! BlockchainActor.WatchBlockchainHeight(blockHeight)
    }
  }

  override val receiveRecover: Receive = {
    case event: OfferAdded => onOfferAdded(event)
    case PublicationRequested => onPublicationRequested()
    case RecoveryCompleted =>
      collaborators.blockchain ! BlockchainActor.RetrieveBlockchainHeight
      broadcastIfNeeded("preventive broadcast after recovery")
  }

  private val finishing: Receive = {
    case TransactionBroadcaster.Finish =>
      deleteMessages(lastSequenceNr)
      self ! PoisonPill
  }

  override val receiveCommand: Receive = finishing orElse {
    case LastBroadcastableOffer(tx) =>
      persist(OfferAdded(tx))(onOfferAdded)

    case PublishBestTransaction =>
      persist(PublicationRequested) { _ =>
        onPublicationRequested()
        broadcastIfNeeded("requested publication")
      }

    case BlockchainActor.BlockchainHeightReached(height) =>
      policy.updateHeight(height)
      broadcastIfNeeded(s"$height reached")

    case msg @ TransactionPublished(tx, _) if tx == policy.bestTransaction =>
    case TransactionPublished(_, unexpectedTx) =>
    case TransactionNotPublished(_, err) =>
  }

  private def broadcastIfNeeded(trigger: String): Unit = {
    if (policy.shouldBroadcast) {
      log.info("Publishing {}: {}", policy.bestTransaction, trigger)
      collaborators.bitcoinPeer ! PublishTransaction(policy.bestTransaction)
    }
  }

  private def onOfferAdded(event: OfferAdded): Unit = {
    policy.addOfferTransaction(event.offer)
  }

  private def onPublicationRequested(): Unit = {
    policy.requestPublication()
  }
}

