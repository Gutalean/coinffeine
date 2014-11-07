package coinffeine.model.bitcoin

import java.io.IOException

import org.bitcoinj.core.PeerAddress

trait NetworkComponent {

  def network: Network

  @throws[IOException]("when addresses cannot be resolved")
  def seedPeerAddresses(): Seq[PeerAddress]
}
