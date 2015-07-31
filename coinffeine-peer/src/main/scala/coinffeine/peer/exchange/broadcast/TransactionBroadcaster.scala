package coinffeine.peer.exchange.broadcast

import akka.actor._
import akka.persistence.{PersistentActor, RecoveryCompleted}

import coinffeine.common.akka.ResubmitTimer
import coinffeine.common.akka.ResubmitTimer.ResubmitTimeout
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

  /** A request for the actor to broadcast the best possible transaction */
  case object PublishBestTransaction

  /** A request for the actor to broadcast the refund transaction. */
  case object PublishRefundTransaction

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
  private case object RefundPublicationRequested extends PersistentEvent

  /** Unused but kept to maintain the binary compatibility, remove after 0.12 */
  @deprecated private case class FinishedWithResult(result: BroadcastResult)
      extends PersistentEvent
  @deprecated sealed trait BroadcastResult
  @deprecated case class SuccessfulBroadcast(publishedTransaction: TransactionPublished)
      extends BroadcastResult
  @deprecated case class FailedBroadcast(cause: Throwable) extends BroadcastResult
}

private class TransactionBroadcaster(
  refund: ImmutableTransaction,
  collaborators: TransactionBroadcaster.Collaborators,
  constants: ProtocolConstants) extends PersistentActor with ActorLogging {
  import TransactionBroadcaster._

  override val persistenceId = "broadcast-with-refund-" + refund.get.getHashAsString
  private val relevantBlocks = Seq(refund.get.getLockTime - constants.refundSafetyBlockCount, refund.get.getLockTime)
  private val policy = new BroadcastPolicyImpl(refund, constants.refundSafetyBlockCount)
  private val resubmitTimer =
    new ResubmitTimer(context, constants.transactionRepublicationInterval)

  override def preStart(): Unit = {
    watchRelevantBlocks()
    super.preStart()
  }

  private def watchRelevantBlocks(): Unit = {
    for (blockHeight <- relevantBlocks) {
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

    case PublishRefundTransaction =>
      persist(RefundPublicationRequested) { _ =>
        onRefundPublicationRequested()
        broadcastIfNeeded("requested refund publication")
      }

    case BlockchainActor.BlockchainHeightReached(height) =>
      policy.updateHeight(height)
      broadcastIfNeeded(s"$height reached")

    case ResubmitTimeout => broadcastIfNeeded("resubmission")

    case TransactionNotPublished(_, ex) => log.error(ex, "Cannot publish transaction")
  }

  private def broadcastIfNeeded(trigger: String): Unit = {
    policy.transactionToBroadcast.foreach(tx => broadcast(tx, trigger))
  }

  private def broadcast(tx: ImmutableTransaction, trigger: String): Unit = {
    log.info("Broadcasting {}: {}", policy.transactionToBroadcast, trigger)
    collaborators.bitcoinPeer ! PublishTransaction(tx)
    resubmitTimer.reset()
  }

  private def onOfferAdded(event: OfferAdded): Unit = {
    policy.addOfferTransaction(event.offer)
  }

  private def onPublicationRequested(): Unit = {
    policy.requestPublication()
  }

  private def onRefundPublicationRequested(): Unit = {
    policy.invalidateOffers()
  }
}

