package coinffeine.peer.bitcoin

import scala.concurrent.{ExecutionContext, Future}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.google.bitcoin.core._
import com.google.common.util.concurrent.{FutureCallback, Futures, Service}

import coinffeine.common.akka.AskPattern
import coinffeine.model.bitcoin._
import coinffeine.model.event.BitcoinConnectionStatus
import coinffeine.model.event.BitcoinConnectionStatus.{NotDownloading, Downloading}
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.event.EventPublisher

class BitcoinPeerActor(peerGroup: PeerGroup, blockchainProps: Props, walletProps: Wallet => Props,
                       keyPairs: Seq[KeyPair], blockchain: AbstractBlockChain,
                       network: NetworkParameters)
  extends Actor with ActorLogging with EventPublisher {

  import coinffeine.peer.bitcoin.BitcoinPeerActor._

  private val blockchainRef = context.actorOf(blockchainProps, "blockchain")
  private val walletRef = context.actorOf(walletProps(createWallet()), "wallet")
  private var connectionStatus =
    BitcoinConnectionStatus(peerGroup.getConnectedPeers.size(), NotDownloading)

  override def postStop(): Unit = {
    log.info("Shutting down peer group")
    peerGroup.stopAndWait()
    log.info("Peer group stopped")
  }

  override def receive: Receive = waitingForInitialization orElse commonHandling

  private def waitingForInitialization: Receive = {
    case Start =>
      peerGroup.addEventListener(PeerGroupListener)
      Futures.addCallback(peerGroup.start(), new PeerGroupCallback(sender()))

    case init: InitializedBitcoinPeerActor => init.start()
  }

  private def createWallet(): Wallet = {
    val wallet = new Wallet(network)
    keyPairs.foreach(wallet.addKey)
    blockchain.addWallet(wallet)
    peerGroup.addWallet(wallet)
    wallet
  }

  private def commonHandling: Receive = {
    case RetrieveConnectionStatus =>
      sender() ! connectionStatus

    case RetrieveBlockchainActor =>
      sender ! BlockchainActorRef(blockchainRef)

    case RetrieveWalletActor =>
      sender() ! WalletActorRef(walletRef)
  }

  private class InitializedBitcoinPeerActor(listener: ActorRef) {

    def start(): Unit = {
      log.info("Connected to peer group, starting blockchain download")
      peerGroup.startBlockChainDownload(PeerGroupListener)
      blockchainRef ! BlockchainActor.Initialize(blockchain)
      listener ! Started(walletRef)
      publishEvent(connectionStatus)
      context.become(started orElse commonHandling)
    }

    val started: Receive = {
      case PublishTransaction(tx) =>
        log.info(s"Publishing transaction $tx to the Bitcoin network")
        Futures.addCallback(
          peerGroup.broadcastTransaction(tx.get),
          new TxBroadcastCallback(tx, sender()),
          context.dispatcher)

      case PeerGroupSize(activePeers) =>
        updateConnectionStatus(connectionStatus.copy(activePeers = activePeers))

      case DownloadStarted(remainingBlocks) =>
        updateConnectionStatus(connectionStatus.copy(blockchainStatus = Downloading(
          totalBlocks = remainingBlocks,
          remainingBlocks = remainingBlocks
        )))

      case DownloadProgress(remainingBlocks) =>
        connectionStatus.blockchainStatus match {
          case Downloading(totalBlocks, previouslyRemainingBlocks)
            if remainingBlocks <= previouslyRemainingBlocks =>
            updateConnectionStatus(connectionStatus.copy(blockchainStatus =
              Downloading(totalBlocks, remainingBlocks)))
          case otherStatus =>
            log.warning("Received download progress ({}) when having status {}",
              remainingBlocks, otherStatus)
        }

      case DownloadCompleted =>
        updateConnectionStatus(connectionStatus.copy(blockchainStatus = NotDownloading))
    }

    private def updateConnectionStatus(newConnectionStatus: BitcoinConnectionStatus): Unit = {
      if (newConnectionStatus != connectionStatus) {
        publishEvent(newConnectionStatus)
      }
      connectionStatus = newConnectionStatus
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

  private class PeerGroupCallback(listener: ActorRef)
    extends FutureCallback[Service.State] {

    def onSuccess(result: Service.State): Unit = {
      self ! new InitializedBitcoinPeerActor(listener)
    }

    def onFailure(cause: Throwable): Unit = {
      log.error(cause, "Cannot connect to peer group")
      listener ! StartFailure(cause)
    }
  }

  private object PeerGroupListener extends AbstractPeerEventListener {
    override def onBlocksDownloaded(peer: Peer, block: Block, blocksLeft: Int): Unit = {
      self ! (if (blocksLeft == 0) DownloadCompleted else DownloadProgress(blocksLeft))
    }

    override def onChainDownloadStarted(peer: Peer, blocksLeft: Int): Unit = {
      self ! DownloadStarted(blocksLeft)
    }

    override def onPeerConnected(peer: Peer, peerCount: Int): Unit = {
      self ! PeerGroupSize(peerCount)
    }

    override def onPeerDisconnected(peer: Peer, peerCount: Int): Unit = {
      self ! PeerGroupSize(peerCount)
    }
  }
}

/** A BitcoinPeerActor handles connections to other peers in the bitcoin network and can:
  *
  * - Return a reference to the BlockchainActor that contains the blockchain derived from the peers
  * - Broadcast a transaction to the peers
  */
object BitcoinPeerActor {

  // Self-messages to manage the connection status
  private case class PeerGroupSize(activePeers: Int)
  private case class DownloadStarted(remainingBlocks: Int)
  private case class DownloadProgress(remainingBlocks: Int)
  private case object DownloadCompleted

  def retrieveBlockchainActor(bitcoinPeer: ActorRef)
                             (implicit ec: ExecutionContext): Future[ActorRef] =
    AskPattern(
      to = bitcoinPeer,
      request = BitcoinPeerActor.RetrieveBlockchainActor,
      errorMessage = s"Cannot retrieve blockchain actor from $bitcoinPeer"
    ).withImmediateReply[BitcoinPeerActor.BlockchainActorRef]().map(_.ref)

  /** A message sent to the peer actor to join to the bitcoin network */
  case object Start
  sealed trait StartResult
  case class Started(walletActor: ActorRef) extends StartResult
  case class StartFailure(cause: Throwable) extends StartResult

  /** A message sent to the peer actor to get the connection status as a [[BitcoinConnectionStatus]] */
  case object RetrieveConnectionStatus

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
  case class BlockchainActorRef(ref: ActorRef)

  /** Ask for the wallet actor reference. To be replied with a [[WalletActorRef]] */
  case object RetrieveWalletActor
  case class WalletActorRef(ref: ActorRef)

  case object NoPeersAvailable extends RuntimeException("There are no peers available")

  trait Component { this: PeerGroupComponent with NetworkComponent with BlockchainComponent
    with PrivateKeysComponent with ConfigComponent =>

    lazy val bitcoinPeerProps: Props = Props(new BitcoinPeerActor(
      createPeerGroup(blockchain),
      BlockchainActor.props(network),
      WalletActor.props,
      keyPairs,
      blockchain,
      network
    ))
  }
}
