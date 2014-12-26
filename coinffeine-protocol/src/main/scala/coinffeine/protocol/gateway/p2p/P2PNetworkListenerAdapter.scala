package coinffeine.protocol.gateway.p2p

import net.tomp2p.p2p.Peer
import net.tomp2p.peers.{PeerAddress, PeerMapChangeListener}
import net.tomp2p.rpc.ObjectDataReply

/** Bridges from what is offered by [[P2PNetwork.Listener]] to the listeners at [[Peer]] */
private class P2PNetworkListenerAdapter(peer: Peer, adaptedListener: P2PNetwork.Listener)
  extends PeerMapChangeListener with ObjectDataReply {

  updateCount()

  override def peerInserted(peerAddress: PeerAddress): Unit = { updateCount() }
  override def peerRemoved(peerAddress: PeerAddress): Unit = { updateCount() }
  override def peerUpdated(peerAddress: PeerAddress): Unit = { updateCount() }

  private def updateCount(): Unit = {
    adaptedListener.peerCountUpdated(peer.getPeerBean.getPeerMap.getAll.size())
  }

  override def reply(sender: PeerAddress, request: Any): AnyRef = {
    val peerId = Number160Util.toPeerId(sender)
    request match {
      case PingProtocol.Ping => adaptedListener.pingedFrom(peerId)
      case PingProtocol.Pong => adaptedListener.pingedBackFrom(peerId)
      case payload: Array[Byte] => adaptedListener.messageReceived(peerId, payload)
    }
    null
  }
}
