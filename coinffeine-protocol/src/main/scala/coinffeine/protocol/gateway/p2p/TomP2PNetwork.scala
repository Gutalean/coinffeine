package coinffeine.protocol.gateway.p2p

import java.net.{InetSocketAddress, NetworkInterface}
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import net.tomp2p.connection.Bindings
import net.tomp2p.futures.FutureDHT
import net.tomp2p.p2p.{Peer, PeerMaker}
import net.tomp2p.storage.Data
import org.slf4j.LoggerFactory

import coinffeine.model.network.{NetworkEndpoint, PeerId}
import coinffeine.protocol.gateway.p2p.P2PNetwork._

object TomP2PNetwork extends P2PNetwork {
  val Log = LoggerFactory.getLogger(getClass)

  override def join(id: PeerId,
                    mode: ConnectionMode,
                    acceptedInterfaces: Seq[NetworkInterface],
                    listener: Listener)
                   (implicit ec: ExecutionContext): Future[P2PNetwork.Session] = {
    for {
      bindings <- configureBindings(mode, acceptedInterfaces)
      peer <- bindPeer(id, mode.localPort, bindings, listener)
      brokerSocketAddress <- mode.brokerAddress.resolve()
      brokerId <- {
        val bootstrap = bootstrapPeer(id, mode, peer, brokerSocketAddress)
        bootstrap.onFailure { case NonFatal(_) => peer.shutdown() }
        bootstrap
      }
    } yield new TomP2PSession(peer, brokerId)
  }

  private def bindPeer(id: PeerId,
                       localPort: Int,
                       bindings: Bindings,
                       listener: Listener)
                      (implicit ec: ExecutionContext): Future[Peer] = Future {
    val peer = new PeerMaker(Number160Util.fromPeerId(id))
      .setPorts(localPort)
      .setBindings(bindings)
      .makeAndListen()
    val adapterListener = new P2PNetworkListenerAdapter(peer, listener)
    peer.setObjectDataReply(adapterListener)
    peer.getPeerBean.getPeerMap.addPeerMapChangeListener(adapterListener)
    peer
  }

  private def bootstrapPeer(id: PeerId, mode: ConnectionMode,
                            peer: Peer,
                            brokerSocketAddress: InetSocketAddress)
                           (implicit ec: ExecutionContext): Future[PeerId] = mode match {
    case StandaloneNode(_) => publishAddress(peer).map(_ => id)

    case AutodetectPeerNode(_, _) =>
      peer.getConfiguration.setBehindFirewall(true)
      for {
        discovery <- peer.discover()
          .setInetAddress(brokerSocketAddress.getAddress)
          .setPorts(brokerSocketAddress.getPort)
          .start()
        bootstrap <- peer.bootstrap().setPeerAddress(discovery.getReporter).start()
        brokerAddress = bootstrap.getBootstrapTo.asScala.head
        _ <- if (isBehindFirewall(peer)) Future.successful(tryToUnpublishAddress(peer))
             else publishAddress(peer)
      } yield {
        Log.info("Successfully connected as {} listening at {} using broker in {}",
          Seq(id, peer.getPeerBean.getServerPeerAddress, brokerAddress): _*)
        Number160Util.toPeerId(brokerAddress)
      }

    case PortForwardedPeerNode(_, _) =>
      for {
        bootstrap <- peer.bootstrap()
          .setInetAddress(brokerSocketAddress.getAddress)
          .setPorts(brokerSocketAddress.getPort)
          .start()
         _ <- publishAddress(peer)
      } yield {
        val brokerAddress = bootstrap.getBootstrapTo.asScala.head
        Log.info("Successfully connected with port forwarding as {} listening at {} using broker in {}",
          Seq(id, peer.getPeerBean.getServerPeerAddress, brokerAddress): _*)
        Number160Util.toPeerId(brokerAddress)
      }
  }

  private def isBehindFirewall(peer: Peer) = peer.getPeerBean.getServerPeerAddress.isFirewalledTCP

  private def publishAddress(peer: Peer): Future[FutureDHT] = {
    Log.debug("Publishing that we are directly accessible at {}",
      peer.getPeerBean.getServerPeerAddress)
    peer.put(peer.getPeerID)
      .setData(new Data(peer.getPeerAddress.toByteArray))
      .start()
  }

  /** We don't need to wait nor need to succeed on unpublishing the address: fire and forget  */
  private def tryToUnpublishAddress(peer: Peer): Unit = {
    Log.warn("This peer can't be accessed directly from others (listening at {})",
      peer.getPeerBean.getServerPeerAddress)
    peer.remove(peer.getPeerID).start()
  }

  private def configureBindings(mode: ConnectionMode,
                                acceptedInterfaces: Seq[NetworkInterface])
                               (implicit ec: ExecutionContext): Future[Bindings] = {
    val bindings = mode match {
      case StandaloneNode(address) => bindingsToSpecificAddress(address)
      case PortForwardedPeerNode(externalAddress, _) => bindingsToSpecificAddress(externalAddress)
      case _ => joiningPeerBindings()
    }
    bindings.map(b => whitelistInterfaces(b, acceptedInterfaces))
  }

  private def bindingsToSpecificAddress(address: NetworkEndpoint)
                                       (implicit ec: ExecutionContext): Future[Bindings] = {
    address.resolve().map { socket =>
      new Bindings(socket.getAddress, socket.getPort, socket.getPort)
    }
  }

  private def joiningPeerBindings(): Future[Bindings] = Future.successful(new Bindings())

  private def whitelistInterfaces(bindings: Bindings,
                                  acceptedInterfaces: Seq[NetworkInterface]): Bindings = {
    acceptedInterfaces.map(_.getName).foreach(bindings.addInterface)
    val ifaces = bindings.getInterfaces.asScala.mkString(",")
    Log.info("Initiating a peer on interfaces [{}]", ifaces)
    bindings
  }
}
