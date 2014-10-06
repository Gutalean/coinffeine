package coinffeine.model.bitcoin

import org.bitcoinj.core.PeerAddress

trait NetworkComponent {
  def network: Network
  def peerAddresses: Seq[PeerAddress]
}
