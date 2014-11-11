package coinffeine.protocol.gateway.p2p

import java.net.NetworkInterface
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import net.tomp2p.connection.Bindings
import net.tomp2p.futures.FutureDHT
import net.tomp2p.p2p.{Peer, PeerMaker}
import net.tomp2p.storage.Data
import org.slf4j.LoggerFactory

import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.p2p.P2PNetwork._

object TomP2PNetwork extends P2PNetwork {
  val Log = LoggerFactory.getLogger(getClass)

  override def join(id: PeerId,
                       mode: ConnectionMode,
                       acceptedInterfaces: Seq[NetworkInterface],
                       listener: Listener)
                      (implicit ec: ExecutionContext): Future[P2PNetwork.Session] = {
    for {
      peer <- bindPeer(id, mode.localPort, acceptedInterfaces, listener)
      brokerId <- {
        val bootstrap = bootstrapPeer(id, mode, peer)
        bootstrap.onFailure { case NonFatal(_) => peer.shutdown() }
        bootstrap
      }
    } yield new TomP2PSession(peer, brokerId)
  }

  private def bindPeer(id: PeerId,
                       localPort: Int,
                       acceptedInterfaces: Seq[NetworkInterface],
                       listener: Listener)
                      (implicit ec: ExecutionContext): Future[Peer] = Future {
    val peer = new PeerMaker(Number160Util.fromPeerId(id))
      .setPorts(localPort)
      .setBindings(configureBindings(acceptedInterfaces))
      .makeAndListen()
    val adapterListener = new P2PNetworkListenerAdapter(peer, listener)
    peer.setObjectDataReply(adapterListener)
    peer.getPeerBean.getPeerMap.addPeerMapChangeListener(adapterListener)
    peer
  }

  private def bootstrapPeer(id: PeerId, mode: ConnectionMode, peer: Peer)
                           (implicit ec: ExecutionContext): Future[PeerId] = mode match {
    case StandaloneNode(_) => publishAddress(peer).map(_ => id)

    case AutodetectPeerNode(_, bootStrapAddress) =>
      peer.getConfiguration.setBehindFirewall(true)
      for {
        discovery <- peer.discover()
          .setInetAddress(bootStrapAddress.getAddress)
          .setPorts(bootStrapAddress.getPort)
          .start()
        bootstrap <- peer.bootstrap().setPeerAddress(discovery.getReporter).start()
        brokerAddress = bootstrap.getBootstrapTo.asScala.head
        _ <- if (hasLocalServerAddress(peer)) Future.successful(tryToUnpublishAddress(peer))
             else publishAddress(peer)
      } yield {
        Log.info("Successfully connected as {} using broker in {}", Seq(id, brokerAddress): _*)
        Number160Util.toPeerId(brokerAddress)
      }

    case PortForwardedPeerNode(_, _) => Future.successful(???) // TODO: port forwarding configuration
  }

  private def hasLocalServerAddress(peer: Peer): Boolean = {
    val address = peer.getPeerBean.getServerPeerAddress.getInetAddress
    address.isLoopbackAddress || address.isSiteLocalAddress
  }

  private def publishAddress(peer: Peer): Future[FutureDHT] = peer.put(peer.getPeerID)
    .setData(new Data(peer.getPeerAddress.toByteArray))
    .start()

  /** We don't need to wait nor need to succeed on unpublishing the address: fire and forget  */
  private def tryToUnpublishAddress(peer: Peer): Unit = {
    Log.warn("This peer can't be accessed directly from others")
    peer.remove(peer.getPeerID).start()
  }

  private def configureBindings(acceptedInterfaces: Seq[NetworkInterface]): Bindings = {
    val bindings = new Bindings()
    acceptedInterfaces.map(_.getName).foreach(bindings.addInterface)
    val ifaces = bindings.getInterfaces.asScala.mkString(",")
    Log.info("Initiating a peer on interfaces {}", ifaces)
    bindings
  }
}
