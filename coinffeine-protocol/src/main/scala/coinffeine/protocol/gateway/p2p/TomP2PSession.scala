package coinffeine.protocol.gateway.p2p

import scala.concurrent.{ExecutionContext, Future}

import net.tomp2p.p2p.Peer

import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.p2p.P2PNetwork.Connection

class TomP2PSession(delegate: Peer, override val brokerId: PeerId)
                   (implicit ec: ExecutionContext) extends P2PNetwork.Session {

  override def connect(peerId: PeerId): Future[Connection] =
    Future.successful(new TomP2PConnection(peerId, delegate))

  override def close() = Future {
    delegate.shutdown()
  }
}
