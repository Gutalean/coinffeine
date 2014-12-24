package coinffeine.protocol.gateway.p2p

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.LazyLogging
import net.tomp2p.connection.PeerConnection
import net.tomp2p.p2p.Peer
import net.tomp2p.peers.PeerAddress

import coinffeine.model.network.PeerId

private class TomP2PConnection(receiverId: PeerId, delegate: Peer)
                              (implicit ec: ExecutionContext)
  extends P2PNetwork.Connection with TomP2PFutureImplicits with LazyLogging {

  import TomP2PConnection._

  private sealed trait CachedPeer {
    private val timestamp: Long = System.currentTimeMillis()
    def hasExpired: Boolean = System.currentTimeMillis() > timestamp + TimeToLive
    def isValid: Boolean = true

    def canBeReused: Boolean = !hasExpired && isValid
    def close(): Unit = {}
    def send(to: PeerId, payload: Array[Byte]): Unit = sendObject(to, payload)
    def ping(to: PeerId): Unit = sendObject(to, PingProtocol.Ping)
    def pingBack(to: PeerId): Unit = sendObject(to, PingProtocol.Pong)

    protected def sendObject(to: PeerId, payloadObject: Any): Unit
  }

  private class DirectPeer(connection: PeerConnection) extends CachedPeer {
    private val hasFailed = new AtomicBoolean(false)

    override def isValid: Boolean = !(connection.isClosed || hasFailed.get())

    override def close(): Unit = {
      connection.close()
    }

    override protected def sendObject(to: PeerId, payloadObject: Any): Unit = {
      delegate.sendDirect(connection)
        .setObject(payloadObject)
        .start()
        .onFailure { case cause =>
          logger.error(s"Cannot send direct message to peer $to", cause)
          hasFailed.set(true)
        }
    }

    override def toString = s"DirectPeer(${connection.getDestination})"
  }

  private class IndirectPeer extends CachedPeer {
    override protected def sendObject(to: PeerId, payloadObject: Any): Unit = {
      delegate.send(Number160Util.fromPeerId(to))
        .setObject(payloadObject)
        .start()
        .onFailure { case cause => logger.error(s"Cannot send message to peer $to", cause) }
    }

    override val toString = "IndirectPeer"
  }

  private var cachedPeer: Option[CachedPeer] = None

  override def send(payload: Array[Byte]): Future[Unit] = peer().map(_.send(receiverId, payload))
  override def ping(): Future[Unit] = peer().map(_.ping(receiverId))
  override def pingBack(): Future[Unit] = peer().map(_.pingBack(receiverId))

  override def close(): Future[Unit] = Future {
    clearConnection()
  }

  private def peer(): Future[CachedPeer] = cachedPeer
    .filter(_.canBeReused)
    .fold(resolvePeer())(Future.successful)

  private def resolvePeer(): Future[CachedPeer] = for {
    maybePeerAddress <- AddressDHT.recover(delegate, receiverId)
    peer = buildCachedPeer(maybePeerAddress)
  } yield {
    updateCachedPeer(peer)
    peer
  }

  private def buildCachedPeer(maybePeerAddress: Option[PeerAddress]): CachedPeer = (for {
    peerAddress <- maybePeerAddress
    connection <- Option(delegate.createPeerConnection(peerAddress, IdleTCPMillisTimeout))
  } yield new DirectPeer(connection)).getOrElse(new IndirectPeer)

  private def updateCachedPeer(peer: CachedPeer): Unit = {
    clearConnection()
    cachedPeer = Some(peer)
    logger.debug(s"Cached $peer for peer $receiverId")
  }

  private def clearConnection(): Unit = {
    cachedPeer.foreach(_.close())
    cachedPeer = None
  }
}

object TomP2PConnection {

  private val IdleTCPMillisTimeout = 6.minutes.toMillis.toInt

  /** Time in milliseconds that peer connections are cached */
  private val TimeToLive = 5.minutes.toMillis
}
