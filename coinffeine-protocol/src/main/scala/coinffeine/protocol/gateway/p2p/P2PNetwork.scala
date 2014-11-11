package coinffeine.protocol.gateway.p2p

import java.net.{NetworkInterface, InetSocketAddress}

import scala.concurrent.{ExecutionContext, Future}

import coinffeine.model.network.PeerId

trait P2PNetwork {
  def join(id: PeerId,
           mode: P2PNetwork.ConnectionMode,
           acceptedInterfaces: Seq[NetworkInterface],
           listener: P2PNetwork.Listener)
          (implicit ec: ExecutionContext): Future[P2PNetwork.Session]
}

object P2PNetwork {
  sealed trait ConnectionMode {
    def localPort: Int
  }

  case class StandaloneNode(address: InetSocketAddress) extends ConnectionMode {
    override def localPort = address.getPort
  }
  case class AutodetectPeerNode(override val localPort: Int, bootstrapAddress: InetSocketAddress)
    extends ConnectionMode
  case class PortForwardedPeerNode(externalAddress: InetSocketAddress,
                                   bootstrapAddress: InetSocketAddress) extends ConnectionMode  {
    override def localPort = externalAddress.getPort
  }

  trait Session {
    def brokerId: PeerId
    def connect(peerId: PeerId): Future[Connection]
    def close(): Future[Unit]
  }

  trait Connection {
    def send(payload: Array[Byte]): Future[Unit]
    def close(): Future[Unit]
  }

  trait Listener {
    def peerCountUpdated(peers: Int): Unit
    def messageReceived(peer: PeerId, payload: Array[Byte]): Unit
  }
}
