package coinffeine.protocol.gateway.p2p

import net.tomp2p.peers.{PeerAddress, Number160}

import coinffeine.model.network.PeerId

private[p2p] object Number160Util {
  def toPeerId(tomp2pId: Number160): PeerId = PeerId(tomp2pId.toString.substring(2))
  def toPeerId(address: PeerAddress): PeerId = toPeerId(address.getID)
  def fromPeerId(peerId: PeerId) = new Number160("0x" + peerId.value)
}
