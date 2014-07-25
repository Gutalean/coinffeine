package coinffeine.peer.bitcoin

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.google.bitcoin.core._
import com.google.common.util.concurrent.{FutureCallback, Futures, Service}

import coinffeine.model.bitcoin._
import coinffeine.peer.config.ConfigComponent

class BitcoinPeerActor(peerGroup: PeerGroup, blockchainProps: Props, walletProps: Props,
                       wallet: Wallet, blockchain: AbstractBlockChain)
  extends Actor with ActorLogging {

  import coinffeine.peer.bitcoin.BitcoinPeerActor._

  override def postStop(): Unit = {
    log.info("Shutting down peer group")
    peerGroup.stopAndWait()
    log.info("Peer group stopped")
  }

  override def receive: Receive = {
    case Start(eventChannel) =>
      Futures.addCallback(peerGroup.start(), new PeerGroupCallback(eventChannel, sender()))
  }

  private class InitializedBitcoinPeerActor(eventChannel: ActorRef, listener: ActorRef) {
    val blockchainRef = context.actorOf(blockchainProps, "blockchain")
    val walletRef = context.actorOf(walletProps, "wallet")

    def start(): Unit = {
      blockchainRef ! BlockchainActor.Initialize(blockchain)
      walletRef ! WalletActor.Initialize(wallet, eventChannel)
      listener ! Started(walletRef)
      context.become(started)
    }

    val started: Receive = {
      case PublishTransaction(tx) =>
        log.info(s"Publishing transaction $tx to the Bitcoin network")
        Futures.addCallback(
          peerGroup.broadcastTransaction(tx.get),
          new TxBroadcastCallback(tx, sender()),
          context.dispatcher)
      case RetrieveBlockchainActor =>
        sender ! BlockchainActorReference(blockchainRef)
    }
  }

  private class TxBroadcastCallback(originalTx: ImmutableTransaction, respondTo: ActorRef)
      extends FutureCallback[MutableTransaction] {

    override def onSuccess(result: MutableTransaction): Unit = {
      log.info(s"Transaction $originalTx successfully broadcast to the Bitcoin network")
      respondTo ! TransactionPublished(originalTx, ImmutableTransaction(result))
    }

    override def onFailure(error: Throwable): Unit = {
      log.error(error, s"Transaction $originalTx failed to be broadcast to the Bitcoin network")
      respondTo ! TransactionNotPublished(originalTx, error)
    }
  }

  private class PeerGroupCallback(eventChannel: ActorRef, listener: ActorRef)
    extends FutureCallback[Service.State] {

    def onSuccess(result: Service.State): Unit = {
      log.info("Connected to peer group, starting blockchain download")
      peerGroup.startBlockChainDownload(new DownloadListener)
      new InitializedBitcoinPeerActor(eventChannel, listener).start()
    }

    def onFailure(cause: Throwable): Unit = {
      log.error(cause, "Cannot connect to peer group")
      listener ! StartFailure(cause)
    }
  }
}

/** A BitcoinPeerActor handles connections to other peers in the bitcoin network and can:
  *
  * - Return a reference to the BlockchainActor that contains the blockchain derived from the peers
  * - Broadcast a transaction to the peers
  */
object BitcoinPeerActor {

  /** A message sent to the peer actor to join to the bitcoin network */
  case class Start(eventChannel: ActorRef)
  sealed trait StartResult
  case class Started(walletActor: ActorRef) extends StartResult
  case class StartFailure(cause: Throwable) extends StartResult

  /** A request for the actor to publish the transaction to its peers so it eventually
    * gets confirmed in the blockchain.
    *
    * @param tx The transaction to be broadcast
    */
  case class PublishTransaction(tx: ImmutableTransaction)

  /** A message sent by the peer actor to confirm that a PublishTransaction request has been
    * completed successfully.
    *
    * @param originalTx The transaction that was used in the PublishTransaction request
    * @param broadcastTx The transaction that was broadcast. This transaction is the canonical
    *                    version of originalTx, which should be used in further requests to the
    *                    blockchain (for example when requesting a certain number of confirmations)
    */
  case class TransactionPublished(
    originalTx: ImmutableTransaction, broadcastTx: ImmutableTransaction)

  /** A message sent by the blockchain actor to notify that a PublishTransaction request could not
    * be completed successfully
    */
  case class TransactionNotPublished(tx: ImmutableTransaction, cause: Throwable)

  /** A request to the actor to retrieve the blockchain actor that contains the blockchain that
    * contains the blocks announced by the peers this actor knows about
    */
  case object RetrieveBlockchainActor

  /** The response to the RetrieveBlockchainActor request */
  case class BlockchainActorReference(ref: ActorRef)

  case object NoPeersAvailable extends RuntimeException("There are no peers available")

  trait Component { this: PeerGroupComponent with NetworkComponent with BlockchainComponent
    with WalletComponent with ConfigComponent =>

    lazy val peerGroup: PeerGroup = createPeerGroup(blockchain)

    lazy val bitcoinPeerProps: Props = Props(new BitcoinPeerActor(
      peerGroup,
      BlockchainActor.props(network),
      WalletActor.props,
      wallet,
      blockchain
    ))
  }
}
