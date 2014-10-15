package coinffeine.peer.exchange.broadcast

import akka.actor._
import akka.persistence.PersistentActor

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BitcoinPeerActor._
import coinffeine.peer.bitcoin.blockchain.BlockchainActor
import coinffeine.peer.exchange.broadcast.ExchangeTransactionBroadcaster._
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.LastBroadcastableOffer

private class DefaultExchangeTransactionBroadcaster(
     refund: ImmutableTransaction,
     collaborators: DefaultExchangeTransactionBroadcaster.Collaborators,
     constants: ProtocolConstants) extends PersistentActor with ActorLogging with Stash {
  import coinffeine.peer.exchange.broadcast.DefaultExchangeTransactionBroadcaster._

  override val persistenceId = "broadcast-with-refund-" + refund.get.getHashAsString
  private val transactions = new ExchangeTransactions(refund, constants.refundSafetyBlockCount)
  private var transactionBeingBroadcast: Option[ImmutableTransaction] = None

  override def preStart(): Unit = {
    watchRelevantBlocks()
    super.preStart()
  }

  private def watchRelevantBlocks(): Unit = {
    for (blockHeight <- transactions.relevantBlocks) {
      collaborators.blockchain ! BlockchainActor.WatchBlockchainHeight(blockHeight)
    }
  }

  override val receiveRecover: Receive = {
    case event: OfferAdded => onOfferAdded(event)
  }

  override val receiveCommand: Receive =  {
    case LastBroadcastableOffer(tx) =>
      persist(OfferAdded(tx))(onOfferAdded)

    case PublishBestTransaction =>
      transactions.requestPublication()
      broadcastIfNeeded()

    case BlockchainActor.BlockchainHeightReached(height) =>
      transactions.updateHeight(height)
      broadcastIfNeeded()

    case msg @ TransactionPublished(tx, _) if tx == transactions.bestTransaction =>
      finishWith(SuccessfulBroadcast(msg))

    case TransactionPublished(_, unexpectedTx) =>
      finishWith(FailedBroadcast(UnexpectedTxBroadcast(unexpectedTx)))

    case TransactionNotPublished(_, err) =>
      finishWith(FailedBroadcast(err))
  }

  private def broadcastIfNeeded(): Unit = {
    if (transactions.shouldBroadcast) {
      transactionBeingBroadcast = Some(transactions.bestTransaction)
      collaborators.bitcoinPeer ! PublishTransaction(transactions.bestTransaction)
    }
  }

  private def onOfferAdded(event: OfferAdded): Unit = {
    transactions.addOfferTransaction(event.offer)
  }

  private def finishWith(result: Any): Unit = {
    collaborators.listener ! result
    context.stop(self)
  }
}

object DefaultExchangeTransactionBroadcaster {

  case class Collaborators(bitcoinPeer: ActorRef, blockchain: ActorRef, listener: ActorRef)

  def props(refund: ImmutableTransaction, collaborators: Collaborators, constants: ProtocolConstants) =
    Props(new DefaultExchangeTransactionBroadcaster(refund, collaborators, constants))

  private case class OfferAdded(offer: ImmutableTransaction)
}
