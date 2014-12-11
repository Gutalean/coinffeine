package coinffeine.protocol.gateway.p2p

import java.net.InetSocketAddress
import scala.concurrent.{ExecutionContext, Future}

import net.tomp2p.futures.FutureDiscover
import net.tomp2p.p2p.{Peer, PeerMaker}
import net.tomp2p.peers.PeerAddress

import coinffeine.model.network.{NetworkEndpoint, PeerId}

/** Factory of regular peers.
  *
  * @constructor
  * @param broker         Endpoint of the node to bootstrap against
  * @param localPort      TCP/UDP port to listen to
  * @param forwardedPort  Optional manually forwarded port (TCP and/or UDP)
  */
private class DefaultPeerFactory(broker: NetworkEndpoint,
                                 localPort: Int,
                                 forwardedPort: Option[Int] = None)
                                (implicit val ec: ExecutionContext) extends PeerFactory {

  override def bindPeer(id: PeerId, bindingsBuilder: BindingsBuilder) = Future {
    val builder = new PeerMaker(Number160Util.fromPeerId(id))
      .setBindings(bindingsBuilder.defaultBindings())
      .setPorts(localPort)
    forwardedPort.foreach(builder.setPorts)
    val peer = builder.makeAndListen()
    peer.getConfiguration.setBehindFirewall(true)
    peer
  }

  override def bootstrapNode(id: PeerId, peer: Peer): Future[PeerId] = for {
    brokerAddress <- broker.resolveAsync()
    brokerPeerAddress <- connectWithBroker(peer, brokerAddress)
    bootstrap <- peer.bootstrap().setPeerAddress(brokerPeerAddress).start()
    _ <- if (isBehindFirewall(peer)) Future.successful(AddressDHT.removeOwnAddress(peer))
         else AddressDHT.storeOwnAddress(peer)
  } yield {
    logger.info("Successfully connected with {} as {} listening at {}",
      broker, id, peer.getPeerAddress)
    Number160Util.toPeerId(brokerPeerAddress)
  }

  private def connectWithBroker(peer: Peer, brokerAddress: InetSocketAddress): Future[PeerAddress] = {
    val discovery = peer.discover()
      .setInetAddress(brokerAddress.getAddress)
      .setPorts(brokerAddress.getPort)
      .start()
    discovery.onComplete { _ =>
      logDiscoveryResult(discovery)
    }
    discovery.transform(
      result => result.getReporter,
      error => new Exception(s"Cannot perform discovery against $brokerAddress", error)
    )
  }

  private def logDiscoveryResult(discovery: FutureDiscover): Unit = {
    logger.info("Discovery result: reporter={}, externalAddress={}, discoveredPorts={}",
      Option(discovery.getReporter).getOrElse("none"),
      Option(discovery.getPeerAddress).getOrElse("unknown"),
      (discovery.isDiscoveredTCP, discovery.isDiscoveredUDP) match {
        case (true, true) => "both"
        case (true, _) => "just TCP"
        case (_, true) => "just UDP"
        case _ => "none"
      }
    )
  }

  private def isBehindFirewall(peer: Peer) = peer.getPeerBean.getServerPeerAddress.isFirewalledTCP
}
