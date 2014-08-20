package coinffeine.peer.bitcoin

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure, Try}

import akka.actor._
import com.google.bitcoin.core._
import com.google.common.util.concurrent.{ListenableFuture, FutureCallback, Futures, Service}

import coinffeine.common.akka.{ServiceActor, AskPattern}
import coinffeine.model.bitcoin._
import coinffeine.model.event.BitcoinConnectionStatus
import coinffeine.model.event.BitcoinConnectionStatus.{NotDownloading, Downloading}
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.event.EventPublisher

class BitcoinPeerActor(peerGroup: PeerGroup, blockchainProps: Props, walletProps: Wallet => Props,
                       keyPairs: Seq[KeyPair], blockchain: AbstractBlockChain,
                       network: NetworkParameters, connectionRetryInterval: FiniteDuration)
  extends Actor with ServiceActor[Unit] with ActorLogging with EventPublisher {

  import coinffeine.peer.bitcoin.BitcoinPeerActor._

  private val blockchainRef = context.actorOf(blockchainProps, "blockchain")
  private val walletRef = context.actorOf(walletProps(createWallet()), "wallet")
  private var connectionStatus =
    BitcoinConnectionStatus(peerGroup.getConnectedPeers.size(), NotDownloading)
  private var retryTimer: Option[Cancellable] = None

  override protected def starting(args: Unit): Receive = {
    peerGroup.addEventListener(PeerGroupListener)
    publishEvent(connectionStatus)
    startConnecting()
    becomeStarted(joining orElse commonHandling)
  }

  override protected def stopping(): Receive = {
    clearRetryTimer()
    if (peerGroup.isRunning) {
      log.info("Shutting down peer group")
      peerGroup.stopAndWait()
      log.info("Peer group stopped")
    }
    becomeStopped()
  }

  private def joining: Receive = {
    case PeerGroupStartResult(Success(_)) =>
      log.info("Connected to peer group, starting blockchain download")
      peerGroup.startBlockChainDownload(PeerGroupListener)
      blockchainRef ! BlockchainActor.Initialize(blockchain)
      become(connected orElse commonHandling)

    case PeerGroupStartResult(Failure(_)) =>
      scheduleRetryTimer()

    case PeerGroupStopResult(_) | RetryConnection =>
      clearRetryTimer()
      startConnecting()

    case PublishTransaction(tx) =>
      log.info(
        s"Not publishing transaction ${tx.get.getHash} since we are not connected to the network")
      sender() ! TransactionNotPublished(tx, new IllegalStateException("Not connected to the network"))
  }

  private def connected: Receive = {
    case PublishTransaction(tx) =>
      log.info(s"Publishing transaction $tx to the Bitcoin network")
      Futures.addCallback(
        peerGroup.broadcastTransaction(tx.get),
        new TxBroadcastCallback(tx, sender()),
        context.dispatcher)
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

  private def startConnecting(): Unit = {
    log.info("Trying to join the bitcoin network")
    replyToSelf(peerGroup.start(), PeerGroupStartResult.apply)
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

  /** Translates a listenable future result into messages sent back to self to safely modify
    * the actor state.
    */
  private def replyToSelf[T](future: ListenableFuture[T], reply: Try[T] => Any): Unit = {
    Futures.addCallback[T](future, new FutureCallback[T] {
      override def onFailure(t: Throwable): Unit = {
        self ! reply(Failure(t))
      }
      override def onSuccess(result: T): Unit = {
        self ! reply(Success(result))
      }
    }, context.dispatcher)
  }

  private def scheduleRetryTimer(): Unit = {
    import context.dispatcher
    retryTimer = Some(context.system.scheduler.scheduleOnce(
      connectionRetryInterval, self, RetryConnection))
  }

  private def clearRetryTimer(): Unit = {
    retryTimer.foreach(_.cancel())
    retryTimer = None
  }
}

/** A BitcoinPeerActor handles connections to other peers in the bitcoin network and can:
  *
  * - Return a reference to the BlockchainActor that contains the blockchain derived from the peers
  * - Broadcast a transaction to the peers
  */
object BitcoinPeerActor {

  private case class PeerGroupStartResult(result: Try[Service.State])
  private case class PeerGroupStopResult(result: Try[Service.State])
  private case object RetryConnection

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

  /** Instruct the peer to join the bitcoin network retrying as much as necessary. */
  @deprecated case object JoinBitcoinNetwork
  /** Instruct the peer to leave the bitcoin network or abort connection in progress */
  @deprecated case object LeaveBitcoinNetwork

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

    lazy val bitcoinPeerProps: Props = {
      val connectionRetryInterval =
        config.getDuration("coinffeine.bitcoin.connectionRetryInterval", TimeUnit.SECONDS).seconds
      Props(new BitcoinPeerActor(
        createPeerGroup(blockchain),
        BlockchainActor.props(network),
        WalletActor.props,
        keyPairs,
        blockchain,
        network,
        connectionRetryInterval
      ))
    }
  }
}
