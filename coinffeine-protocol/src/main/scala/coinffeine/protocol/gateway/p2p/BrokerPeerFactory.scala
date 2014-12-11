package coinffeine.protocol.gateway.p2p

import java.net.InetAddress
import scala.concurrent.ExecutionContext

import net.tomp2p.p2p.{Peer, PeerMaker}

import coinffeine.model.network.{NetworkEndpoint, PeerId}

/** Peer factory for broker nodes */
private class BrokerPeerFactory(broker: NetworkEndpoint)(implicit val ec: ExecutionContext)
  extends PeerFactory {

  override def bindPeer(id: PeerId, bindingsBuilder: BindingsBuilder) = for {
    address <- broker.resolveAsync()
  } yield {
    val peer = new PeerMaker(Number160Util.fromPeerId(id))
      .setBindings(bindingsBuilder.bindToAddress(address))
      .setPorts(broker.port)
      .makeAndListen()
    forceAddress(peer, address.getAddress)
    peer
  }

  override def bootstrapNode(id: PeerId, dht: Peer) = for {
    _ <- AddressDHT.storeOwnAddress(dht)
  } yield {
    logger.info("Successfully started at {}", dht.getPeerAddress)
    id
  }

  private def forceAddress(peer: Peer, address: InetAddress): Unit = {
    peer.getPeerBean.setServerPeerAddress(peer.getPeerAddress.changeAddress(address))
  }
}
