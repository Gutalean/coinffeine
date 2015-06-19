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

  case class Collaborators(bitcoinPeer: ActorRef, blockchain: ActorRef, listener: ActorRef)

  def props(
    refund: ImmutableTransaction, collaborators: Collaborators, constants: ProtocolConstants) =
    Props(new TransactionBroadcaster(refund, collaborators, constants))

  private case class OfferAdded(offer: ImmutableTransaction) extends PersistentEvent
  private case object PublicationRequested extends PersistentEvent
  private case class FinishedWithResult(result: BroadcastResult) extends PersistentEvent
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
    case event: FinishedWithResult => onFinished(event)
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
      finishWith(SuccessfulBroadcast(msg))

    case TransactionPublished(_, unexpectedTx) =>
      finishWith(FailedBroadcast(UnexpectedTxBroadcast(unexpectedTx)))

    case TransactionNotPublished(_, err) =>
      finishWith(FailedBroadcast(err))
  }

  private def broadcastIfNeeded(trigger: String): Unit = {
    if (policy.shouldBroadcast) {
      log.info("Publishing {}: {}", policy.bestTransaction, trigger)
      collaborators.bitcoinPeer ! PublishTransaction(policy.bestTransaction)
    }
  }

  private def finishWith(result: BroadcastResult): Unit = {
    persist(FinishedWithResult(result))(onFinished)
  }

  private def onOfferAdded(event: OfferAdded): Unit = {
    policy.addOfferTransaction(event.offer)
  }

  private def onPublicationRequested(): Unit = {
    policy.requestPublication()
  }

  private def onFinished(event: FinishedWithResult): Unit = {
    policy.done()
    collaborators.listener ! event.result
    context.become(finishing)
  }
}

