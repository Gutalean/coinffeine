package coinffeine.model.bitcoin

import com.google.bitcoin.core.PeerGroup

trait SimplePeerGroupComponent extends PeerGroupComponent {

  this: NetworkComponent with BlockchainComponent =>

  override lazy val peerGroup = {
    val peerGroup = new PeerGroup(network, blockchain)
    peerAddresses.foreach(peerGroup.addAddress)
    peerGroup
  }
}
