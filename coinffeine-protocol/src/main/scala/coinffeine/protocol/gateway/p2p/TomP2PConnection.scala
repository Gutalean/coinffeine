package coinffeine.protocol.gateway.p2p

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import net.tomp2p.connection.PeerConnection
import net.tomp2p.p2p.Peer
import net.tomp2p.peers.PeerAddress
import org.slf4j.LoggerFactory

import coinffeine.model.network.PeerId

class TomP2PConnection(delegate: Peer, override val brokerId: PeerId)
                      (implicit ec: ExecutionContext) extends P2PNetwork.Connection {

  import TomP2PConnection._

  private sealed trait CachedPeer {
    private val timestamp: Long = System.currentTimeMillis()
    def hasExpired: Boolean = System.currentTimeMillis() > timestamp + TimeToLive
    def isValid: Boolean = true

    def canBeReused: Boolean = !hasExpired && isValid
    def close(): Unit = {}
    def send(to: PeerId, payload: Array[Byte]): Unit
  }

  private class DirectPeer(connection: PeerConnection) extends CachedPeer {
    private val hasFailed = new AtomicBoolean(false)

    override def isValid: Boolean = connection.isClosed || hasFailed.get()

    override def close(): Unit = {
      connection.close()
    }

    override def send(to: PeerId, payload: Array[Byte]): Unit = {
      delegate.sendDirect(connection)
        .setObject(payload)
        .start()
        .onFailure { case cause =>
          Log.error("Cannot send direct message to peer {}", Seq(to, cause): _*)
          hasFailed.set(true)
        }
    }

    override def toString = s"DirectPeer($connection)"
  }

  private class IndirectPeer extends CachedPeer {
    override def send(to: PeerId, payload: Array[Byte]): Unit = {
      delegate.send(Number160Util.fromPeerId(to))
        .setObject(payload)
        .start()
        .onFailure { case cause =>
          Log.error("Cannot send message to peer {}", Seq(to, cause): _*)
        }
    }

    override val toString = "IndirectPeer"
  }

  private var cachedPeers: Map[PeerId, CachedPeer] = Map.empty

  override def send(to: PeerId, payload: Array[Byte]): Unit = {
    peerById(to)
      .map { peer => peer.send(to, payload) }
      .onFailure { case cause =>
        Log.error(s"Cannot send message to $to", cause)
      }
  }

  private def peerById(peerId: PeerId): Future[CachedPeer] = cachedPeers.get(peerId)
    .filter(_.canBeReused)
    .fold(resolvePeer(peerId))(Future.successful)

  private def resolvePeer(peerId: PeerId): Future[CachedPeer] = for {
    maybePeerAddress <- tryToResolvePeerAddress(peerId)
    peer = buildCachedPeer(maybePeerAddress)
  } yield {
    updateCachedPeer(peerId, peer)
    peer
  }

  private def tryToResolvePeerAddress(peerId: PeerId): Future[Option[PeerAddress]] =
    delegate.get(Number160Util.fromPeerId(peerId))
      .start()
      .map { dhtEntry => Some(new PeerAddress(dhtEntry.getData.getData)) }
      .recover { case NonFatal(_) => None }

  private def buildCachedPeer(maybePeerAddress: Option[PeerAddress]): CachedPeer = (for {
    peerAddress <- maybePeerAddress
    connection <- Option(delegate.createPeerConnection(peerAddress, IdleTCPMillisTimeout))
  } yield new DirectPeer(connection)).getOrElse(new IndirectPeer)

  private def updateCachedPeer(id: PeerId, peer: CachedPeer): Unit = {
    synchronized {
      clearConnection(id)
      val prev = cachedPeers.get(id)
      cachedPeers += id -> peer
      Log.debug("{}: Cached {} for peer {} (previously {})", Seq(this, peer, id, prev): _*)
    }
  }

  private def clearConnection(peer: PeerId): Unit = synchronized {
    cachedPeers.get(peer).foreach(_.close())
    cachedPeers -= peer
  }

  override def close() = Future {
    cachedPeers.values.foreach(_.close())
    delegate.shutdown()
  }
}

object TomP2PConnection {
  private val Log = LoggerFactory.getLogger(classOf[TomP2PConnection])

  private val IdleTCPMillisTimeout = 6.minutes.toMillis.toInt

  /** Time in milliseconds that peer connections are cached */
  private val TimeToLive = 5.minutes.toMillis
}
