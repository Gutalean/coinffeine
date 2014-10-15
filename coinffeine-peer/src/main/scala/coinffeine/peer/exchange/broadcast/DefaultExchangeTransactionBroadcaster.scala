package coinffeine.peer.exchange.broadcast

import scala.concurrent.duration._

import akka.actor._
import akka.pattern.ask
import akka.persistence.PersistentActor
import akka.util.Timeout

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
  import DefaultExchangeTransactionBroadcaster._

  override val persistenceId = "broadcast-with-refund-" + refund.get.getHashAsString
  private val transactions = new ExchangeTransactions(refund)

  override def preStart(): Unit = {
    setTimePanicFinish()
    super.preStart()
  }

  private def setTimePanicFinish(): Unit = {
    val panicBlock = refund.get.getLockTime - constants.refundSafetyBlockCount
    autoNotifyBlockchainHeightWith(panicBlock, PublishBestTransaction)
  }

  override val receiveRecover: Receive = {
    case event: OfferAdded => onOfferAdded(event)
  }

  override val receiveCommand: Receive =  {
    case LastBroadcastableOffer(tx) =>
      persist(OfferAdded(tx))(onOfferAdded)

    case PublishBestTransaction =>
      val bestTransaction = transactions.bestTransaction.get
      if (bestTransaction.isTimeLocked) {
        autoNotifyBlockchainHeightWith(bestTransaction.getLockTime, ReadyForBroadcast)
      } else {
        self ! ReadyForBroadcast
      }
      context.become(readyForBroadcast(ImmutableTransaction(bestTransaction)))
  }

  private def onOfferAdded(event: OfferAdded): Unit = {
    transactions.addOfferTransaction(event.offer)
  }

  private def readyForBroadcast(offer: ImmutableTransaction): Receive = {
    case ReadyForBroadcast =>
      collaborators.bitcoinPeer ! PublishTransaction(offer)
      context.become(broadcastCompleted(offer))
  }

  private def broadcastCompleted(txToPublish: ImmutableTransaction): Receive = {
    case msg @ TransactionPublished(`txToPublish`, _) =>
      finishWith(SuccessfulBroadcast(msg))
    case TransactionPublished(_, unexpectedTx) =>
      finishWith(FailedBroadcast(UnexpectedTxBroadcast(unexpectedTx)))
    case TransactionNotPublished(_, err) =>
      finishWith(FailedBroadcast(err))
  }

  private def finishWith(result: Any): Unit = {
    collaborators.listener ! result
    context.stop(self)
  }

  private def autoNotifyBlockchainHeightWith(height: Long, msg: Any): Unit = {
    import context.dispatcher
    implicit val timeout = Timeout(1.day)
    (collaborators.blockchain ? BlockchainActor.WatchBlockchainHeight(height))
      .mapTo[BlockchainActor.BlockchainHeightReached]
      .onSuccess { case _ =>
      self ! msg
    }
  }
}

object DefaultExchangeTransactionBroadcaster {

  case class Collaborators(bitcoinPeer: ActorRef, blockchain: ActorRef, listener: ActorRef)

  def props(refund: ImmutableTransaction, collaborators: Collaborators, constants: ProtocolConstants) =
    Props(new DefaultExchangeTransactionBroadcaster(refund, collaborators, constants))

  private case object ReadyForBroadcast

  private case class OfferAdded(offer: ImmutableTransaction)
}
