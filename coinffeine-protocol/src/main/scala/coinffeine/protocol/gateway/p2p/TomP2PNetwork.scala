package coinffeine.protocol.gateway.p2p

import java.net.{InetSocketAddress, NetworkInterface}
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import com.typesafe.scalalogging.StrictLogging
import net.tomp2p.connection.Bindings
import net.tomp2p.futures.FutureBootstrap
import net.tomp2p.p2p.{Peer, PeerMaker}

import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.p2p.P2PNetwork._

object TomP2PNetwork extends P2PNetwork with StrictLogging {

  private class SessionFactory(id: PeerId,
                               mode: ConnectionMode,
                               bindingsBuilder: BindingsBuilder,
                               listener: Listener)
                              (implicit ec: ExecutionContext) {

    def build(): Future[TomP2PSession] = for {
      bindings <- configureBindings()
      peer <- bindPeer(bindings)
      brokerSocketAddress <- mode.brokerAddress.resolveAsync()
      brokerId <- {
        val bootstrap = bootstrapPeer(peer, brokerSocketAddress)
        bootstrap.onFailure { case NonFatal(_) => peer.shutdown() }
        bootstrap
      }
    } yield new TomP2PSession(peer, brokerId)

    private def bindPeer(bindings: Bindings): Future[Peer] = Future {
      val peer = new PeerMaker(Number160Util.fromPeerId(id))
        .setPorts(mode.localPort)
        .setBindings(bindings)
        .makeAndListen()
      val adapterListener = new P2PNetworkListenerAdapter(peer, listener)
      peer.setObjectDataReply(adapterListener)
      peer.getPeerBean.getPeerMap.addPeerMapChangeListener(adapterListener)
      peer
    }

    private def bootstrapPeer(peer: Peer, brokerSocketAddress: InetSocketAddress): Future[PeerId] =
      mode match {
        case _: StandaloneNode => publishAddress(peer).map(_ => id)

        case _: AutodetectPeerNode =>
          peer.getConfiguration.setBehindFirewall(true)
          for {
            discovery <- peer.discover()
              .setInetAddress(brokerSocketAddress.getAddress)
              .setPorts(brokerSocketAddress.getPort)
              .start()
            bootstrap <- peer.bootstrap().setPeerAddress(discovery.getReporter).start()
            _ <- if (isBehindFirewall(peer)) Future.successful(tryToUnpublishAddress(peer))
                 else publishAddress(peer)
          } yield completeConnection(peer, bootstrap, connectedWith = "automatic configuration")

        case _: PortForwardedPeerNode =>
          for {
            bootstrap <- peer.bootstrap()
              .setInetAddress(brokerSocketAddress.getAddress)
              .setPorts(brokerSocketAddress.getPort)
              .start()
            _ <- publishAddress(peer)
          } yield completeConnection(peer, bootstrap, connectedWith = "port forwarding")
      }

    private def completeConnection(peer: Peer,
                                   bootstrap: FutureBootstrap,
                                   connectedWith: String): PeerId = {
      val brokerAddress = bootstrap.getBootstrapTo.asScala.head
      logger.info("Successfully connected with {} as {} listening at {} using broker in {}",
        connectedWith, id, peer.getPeerBean.getServerPeerAddress, brokerAddress)
      Number160Util.toPeerId(brokerAddress)
    }

    private def isBehindFirewall(peer: Peer) = peer.getPeerBean.getServerPeerAddress.isFirewalledTCP

    private def publishAddress(peer: Peer): Future[Unit] = {
      logger.debug("Publishing that we are directly accessible at {}",
        peer.getPeerBean.getServerPeerAddress)
      AddressDHT.storeOwnAddress(peer)
    }

    /** We don't need to wait nor need to succeed on unpublishing the address: fire and forget  */
    private def tryToUnpublishAddress(peer: Peer): Unit = {
      logger.warn("This peer can't be accessed directly from others (listening at {})",
        peer.getPeerBean.getServerPeerAddress)
      peer.remove(peer.getPeerID).start()
    }

    private def configureBindings(): Future[Bindings] = mode match {
      case StandaloneNode(address) =>
        address.resolveAsync().map(bindingsBuilder.bindToAddress)
      case PortForwardedPeerNode(externalPort, brokerAddress) =>
        new ExternalIpProbe().detect(id, brokerAddress).map { externalIp =>
          bindingsBuilder.bindToAddress(new InetSocketAddress(externalIp, externalPort))
        }
      case _ => Future.successful(bindingsBuilder.defaultBindings())
    }
  }

  override def join(id: PeerId,
                    mode: ConnectionMode,
                    acceptedInterfaces: Seq[NetworkInterface],
                    listener: Listener)
                   (implicit ec: ExecutionContext): Future[P2PNetwork.Session] =
    new SessionFactory(id, mode, new BindingsBuilder(acceptedInterfaces), listener).build()
}
