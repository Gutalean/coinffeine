package coinffeine.protocol.gateway.p2p

import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.LazyLogging
import net.tomp2p.p2p.Peer

import coinffeine.common.ScalaFutureImplicits
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.p2p.P2PNetwork.Listener

/** Abstract peer factory to be refined depending on the type of the peer to build */
private abstract class PeerFactory
  extends ScalaFutureImplicits with TomP2PFutureImplicits with LazyLogging {

  implicit protected val ec: ExecutionContext

  def build(id: PeerId,
            bindingsBuilder: BindingsBuilder,
            listener: Listener): Future[(Peer, PeerId)] = {
    val dhtConstruction = for {
      peer <- bindPeer(id, bindingsBuilder)
    } yield {
      configureDHT(peer, listener)
      peer
    }

    shutdownOnError(dhtConstruction){ dht => for {
      brokerId <- bootstrapNode(id, dht)
    } yield (dht, brokerId)}
  }

  /** Create and bind a [[Peer]] */
  protected def bindPeer(id: PeerId, bindingsBuilder: BindingsBuilder): Future[Peer]

  /** Setups the network configuration of a DHT node
    *
    * @param id   Node's peer id
    * @param dht  Node to configure
    * @return     The broker id when successful
    */
  protected def bootstrapNode(id: PeerId, dht: Peer): Future[PeerId]

  private def configureDHT(peer: Peer, listener: Listener): Unit = {
    val adapterListener = new P2PNetworkListenerAdapter(peer, listener)
    peer.setObjectDataReply(adapterListener)
    peer.getPeerBean.getPeerMap.addPeerMapChangeListener(adapterListener)
  }

  /** Make sure that after any failure in the given action the peer is gracefully shutdown */
  private def shutdownOnError[A](dhtConstruction: Future[Peer])
                                (action: Peer => Future[A]): Future[A] = for {
    dht <- dhtConstruction
    actionResult <- action(dht).failureAction {
      logger.warn("Shutting down peer {} after failed bootstrap", dht.getPeerID)
      Future { dht.shutdown() }
    }
  } yield actionResult
}

