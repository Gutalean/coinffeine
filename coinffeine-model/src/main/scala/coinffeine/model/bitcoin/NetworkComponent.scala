package coinffeine.model.bitcoin

import com.google.bitcoin.core.PeerAddress

trait NetworkComponent {
  def network: Network
  def peerAddresses: Seq[PeerAddress]
}
