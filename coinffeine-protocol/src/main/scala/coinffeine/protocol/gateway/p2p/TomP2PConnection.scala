package coinffeine.protocol.gateway.p2p

import scala.concurrent.Future

import coinffeine.model.network.PeerId

private class TomP2PConnection(receiverId: PeerId, @deprecated val session: TomP2PSession) extends P2PNetwork.Connection {

  override def send(payload: Array[Byte]): Future[Unit] = {
    session.send(receiverId, payload)
    Future.successful {}
  }

  override def close(): Future[Unit] = {
    // TODO
    Future.successful {}
  }
}
