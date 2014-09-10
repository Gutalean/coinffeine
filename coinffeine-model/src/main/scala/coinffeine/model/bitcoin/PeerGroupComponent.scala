package coinffeine.model.bitcoin

import com.google.bitcoin.core.{AbstractBlockChain, PeerGroup}

/** Determines the strategy to connect with the Bitcoin network */
trait PeerGroupComponent {
  def peerGroup: PeerGroup
}
