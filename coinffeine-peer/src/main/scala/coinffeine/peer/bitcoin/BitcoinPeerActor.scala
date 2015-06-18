package coinffeine.peer.bitcoin

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import akka.actor._
import akka.pattern._
import com.google.common.util.concurrent.Service.State
import com.google.common.util.concurrent._
import org.bitcoinj.core._
import org.bitcoinj.net.discovery.DnsDiscovery

import coinffeine.common.akka.event.CoinffeineEventProducer
import coinffeine.common.akka.{AskPattern, ServiceLifecycle}
import coinffeine.model.bitcoin._
import coinffeine.model.network.NetworkEndpoint
import coinffeine.peer.bitcoin.blockchain.BlockchainActor
import coinffeine.peer.bitcoin.platform.BitcoinPlatform
import coinffeine.peer.bitcoin.wallet.{DefaultWalletActor, SmartWallet}
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.events.bitcoin.{BlockchainStatusChanged, ActiveBitcoinPeersChanged}

class BitcoinPeerActor(delegates: BitcoinPeerActor.Delegates,
                       platform: BitcoinPlatform,
                       networkComponent: NetworkComponent,
                       connectionRetryInterval: FiniteDuration)
  extends Actor with ServiceLifecycle[Unit] with ActorLogging with CoinffeineEventProducer {

  import BitcoinPeerActor._

  private val blockchainRef = context.actorOf(delegates.blockchainActor(platform), "blockchain")
  private val walletRef = context.actorOf(delegates.walletActor(platform.wallet), "wallet")
  private var retryTimer: Option[Cancellable] = None

  override protected def onStart(args: Unit) = {
    platform.peerGroup.addEventListener(ConnectedPeersListener)
    platform.peerGroup.addListener(PeerGroupLifecycleListener, context.dispatcher)
    startConnecting()
    BecomeStarted(joining orElse commonHandling)
  }

  override protected def onStop() = {
    clearRetryTimer()
    if (platform.peerGroup.isRunning) {
      log.info("Shutting down peer group")
      platform.peerGroup.stopAsync()
      platform.peerGroup.awaitTerminated()
      log.info("Peer group stopped")
    }
    platform.blockchain.getBlockStore.close()
    platform.blockchain match {
      case blockchain: FullPrunedBlockChain => FullPrunedBlockChainUtils.shutdown(blockchain)
      case _ => // Do nothing
    }
    BecomeStopped
  }

  private def joining: Receive = {

    case SeedPeers(addresses) =>
      if (addresses.nonEmpty) addresses.foreach(platform.peerGroup.addAddress)
      else platform.peerGroup.addPeerDiscovery(new DnsDiscovery(networkComponent.network))
      platform.peerGroup.startAsync()

    case CannotResolveSeedPeers(cause) =>
      log.error(cause, "Cannot resolve seed peer addresses")
      scheduleRetryTimer()

    case PeerGroupStartResult(Success(_)) =>
      log.info("Connected to peer group, starting blockchain download")
      platform.peerGroup.startBlockChainDownload(
        new BlockchainDownloadListener(publishBlockchainStatus))
      become(connected orElse commonHandling)

    case PeerGroupStartResult(Failure(_)) =>
      scheduleRetryTimer()

    case RetryConnection =>
      clearRetryTimer()
      startConnecting()

    case PublishTransaction(tx) =>
      log.info(
        s"Not publishing transaction ${tx.get.getHash} since we are not connected to the network")
      sender() ! TransactionNotPublished(tx, new IllegalStateException("Not connected to the network"))
  }

  private def connected: Receive = {
    case PublishTransaction(tx) =>
      val name = s"broadcast-${tx.get.getHash}"
      if (context.child(name).isEmpty) {
        context.actorOf(delegates.transactionPublisher(platform.peerGroup, tx, sender()), name)
      }
  }

  private def commonHandling: Receive = {
    case RetrieveBlockchainActor =>
      sender ! BlockchainActorRef(blockchainRef)

    case RetrieveWalletActor =>
      sender() ! WalletActorRef(walletRef)
  }

  private def startConnecting(): Unit = {
    import context.dispatcher
    log.info("Trying to join the bitcoin network")

    def resolvePeerAddress(peer: NetworkEndpoint) = peer.resolveAsync().map { new PeerAddress(_) }

    Future.sequence(platform.seedPeers.map(resolvePeerAddress))
      .map(SeedPeers.apply)
      .recover { case NonFatal(cause) => CannotResolveSeedPeers(cause) }
      .pipeTo(self)
  }

  private def publishBlockchainStatus(status: BlockchainStatus): Unit = {
    publish(BlockchainStatusChanged(status))
  }

  private object ConnectedPeersListener extends AbstractPeerEventListener {

    override def onPeerConnected(peer: Peer, peerCount: Int): Unit = {
      publish(ActiveBitcoinPeersChanged(peerCount))
    }

    override def onPeerDisconnected(peer: Peer, peerCount: Int): Unit = {
      publish(ActiveBitcoinPeersChanged(peerCount))
    }
  }

  private object PeerGroupLifecycleListener extends Service.Listener {

    override def starting(): Unit = {
      self ! PeerGroupStartResult(Success {})
    }

    override def failed(from: State, cause: Throwable): Unit = {
      if (from == State.STARTING) {
        self ! PeerGroupStartResult(Failure(cause))
      }
    }
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

  private case class SeedPeers(peers: Seq[PeerAddress])
  private case class CannotResolveSeedPeers(cause: Throwable)
  private case class PeerGroupStartResult(result: Try[Unit])
  private case object RetryConnection

  def retrieveBlockchainActor(bitcoinPeer: ActorRef)
                             (implicit ec: ExecutionContext): Future[ActorRef] =
    AskPattern(
      to = bitcoinPeer,
      request = BitcoinPeerActor.RetrieveBlockchainActor,
      errorMessage = s"Cannot retrieve blockchain actor from $bitcoinPeer"
    ).withImmediateReply[BitcoinPeerActor.BlockchainActorRef]().map(_.ref)

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

  trait Delegates {
    def transactionPublisher(peerGroup: PeerGroup,
                             tx: ImmutableTransaction,
                             listener: ActorRef): Props
    def walletActor(wallet: SmartWallet): Props
    def blockchainActor(platform: BitcoinPlatform): Props
  }

  trait Component {

    this: BitcoinPlatform.Component with NetworkComponent with ConfigComponent =>

    lazy val bitcoinPeerProps: Props = {
      val settings = configProvider.bitcoinSettings()
      Props(new BitcoinPeerActor(
        new Delegates {
          override def transactionPublisher(peerGroup: PeerGroup,
                                            tx: ImmutableTransaction,
                                            listener: ActorRef): Props =
            Props(new TransactionPublisher(tx, peerGroup, listener, settings.rebroadcastTimeout))
          override def walletActor(wallet: SmartWallet) =
            DefaultWalletActor.props(wallet)
          override def blockchainActor(platform: BitcoinPlatform) =
            BlockchainActor.props(platform.blockchain, platform.wallet.delegate)
        },
        bitcoinPlatform,
        this,
        settings.connectionRetryInterval
      ))
    }
  }
}
