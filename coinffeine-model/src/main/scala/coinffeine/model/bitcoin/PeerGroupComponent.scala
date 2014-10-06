package coinffeine.model.bitcoin

import org.bitcoinj.core.{AbstractBlockChain, PeerGroup}

/** Determines the strategy to connect with the Bitcoin network */
trait PeerGroupComponent {
  def peerGroup: PeerGroup
}
