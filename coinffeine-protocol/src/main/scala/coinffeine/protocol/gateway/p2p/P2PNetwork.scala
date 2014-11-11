package coinffeine.protocol.gateway.p2p

import java.net.{NetworkInterface, InetSocketAddress}

import scala.concurrent.{ExecutionContext, Future}

import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.p2p.P2PNetwork.ConnectionMode

trait P2PNetwork {
  type Conn <: P2PNetwork.Connection

  def connect(id: PeerId,
              mode: ConnectionMode,
              acceptedInterfaces: Seq[NetworkInterface],
              listener: P2PNetwork.Listener)
             (implicit ec: ExecutionContext): Future[Conn]
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

  trait Connection {
    def brokerId: PeerId
    def close(): Future[Unit]
    def send(to: PeerId, payload: Array[Byte]): Unit
  }

  trait Listener {
    def peerCountUpdated(peers: Int): Unit
    def messageReceived(peer: PeerId, payload: Array[Byte]): Unit
  }
}
