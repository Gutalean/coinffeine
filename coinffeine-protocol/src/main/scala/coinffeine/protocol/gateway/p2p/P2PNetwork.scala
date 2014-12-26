package coinffeine.protocol.gateway.p2p

import java.net.NetworkInterface
import scala.concurrent.{ExecutionContext, Future}

import coinffeine.model.network.{NetworkEndpoint, PeerId}

trait P2PNetwork {
  def join(id: PeerId,
           mode: P2PNetwork.ConnectionMode,
           acceptedInterfaces: Seq[NetworkInterface],
           listener: P2PNetwork.Listener)
          (implicit ec: ExecutionContext): Future[P2PNetwork.Session]
}

object P2PNetwork {

  sealed trait ConnectionMode {
    def brokerAddress: NetworkEndpoint
  }

  case class StandaloneNode(override val brokerAddress: NetworkEndpoint) extends ConnectionMode
  case class AutodetectPeerNode(localPort: Int, override val brokerAddress: NetworkEndpoint)
    extends ConnectionMode
  case class PortForwardedPeerNode(externalPort: Int, override val brokerAddress: NetworkEndpoint)
    extends ConnectionMode

  trait Session {
    def brokerId: PeerId
    def connect(peerId: PeerId): Future[Connection]
    def close(): Future[Unit]
  }

  trait Connection {
    def send(payload: Array[Byte]): Future[Unit]
    def ping(): Future[Unit]
    def pingBack(): Future[Unit]
    def close(): Future[Unit]
  }

  trait Listener {
    def peerCountUpdated(peers: Int): Unit
    def messageReceived(peer: PeerId, payload: Array[Byte]): Unit
    def pingedFrom(peerId: PeerId): Unit
    def pingedBackFrom(peerId: PeerId): Unit
  }
}
