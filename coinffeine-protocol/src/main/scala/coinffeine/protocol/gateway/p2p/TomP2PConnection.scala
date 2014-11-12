package coinffeine.protocol.gateway.p2p

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

import net.tomp2p.connection.PeerConnection
import net.tomp2p.p2p.Peer
import net.tomp2p.peers.PeerAddress
import org.slf4j.LoggerFactory

import coinffeine.model.network.PeerId

private class TomP2PConnection(receiverId: PeerId, delegate: Peer)
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

    override def isValid: Boolean = !(connection.isClosed || hasFailed.get())

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

    override def toString = s"DirectPeer(${connection.getDestination})"
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

  private var cachedPeer: Option[CachedPeer] = None

  override def send(payload: Array[Byte]): Future[Unit] = {
    peer().map(_.send(receiverId, payload))
  }

  override def close(): Future[Unit] = Future {
    clearConnection()
  }

  private def peer(): Future[CachedPeer] = cachedPeer
    .filter(_.canBeReused)
    .fold(resolvePeer())(Future.successful)

  private def resolvePeer(): Future[CachedPeer] = for {
    maybePeerAddress <- tryToResolvePeerAddress(receiverId)
    peer = buildCachedPeer(maybePeerAddress)
  } yield {
    updateCachedPeer(peer)
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

  private def updateCachedPeer(peer: CachedPeer): Unit = {
    clearConnection()
    cachedPeer = Some(peer)
    Log.debug("Cached {} for peer {}", Seq(peer, receiverId): _*)
  }

  private def clearConnection(): Unit = {
    cachedPeer.foreach(_.close())
    cachedPeer = None
  }
}

object TomP2PConnection {

  private val Log = LoggerFactory.getLogger(classOf[TomP2PConnection])

  private val IdleTCPMillisTimeout = 6.minutes.toMillis.toInt

  /** Time in milliseconds that peer connections are cached */
  private val TimeToLive = 5.minutes.toMillis
}
