package coinffeine.peer.bitcoin.platform

import org.bitcoinj.core.{FullPrunedBlockChain, AbstractBlockChain, PeerGroup}

import coinffeine.model.bitcoin.Network
import coinffeine.model.network.NetworkEndpoint
import coinffeine.peer.bitcoin.wallet.SmartWallet

trait BitcoinPlatform {
  def network: Network
  def blockchain: AbstractBlockChain
  def peerGroup: PeerGroup
  def wallet: SmartWallet
  def seedPeers: Seq[NetworkEndpoint]

  def fullPrunedBlockchain: Option[FullPrunedBlockChain] = blockchain match {
    case chain: FullPrunedBlockChain => Some(chain)
    case _ => None
  }
}

object BitcoinPlatform {
  trait Component {
    def bitcoinPlatform: BitcoinPlatform
  }
}
