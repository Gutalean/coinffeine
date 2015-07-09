package coinffeine.peer.bitcoin.platform

import org.bitcoinj.core.{AbstractBlockChain, PeerGroup}

import coinffeine.model.bitcoin.Network
import coinffeine.model.network.NetworkEndpoint
import coinffeine.peer.bitcoin.wallet.SmartWallet

trait BitcoinPlatform {
  def network: Network
  def blockchain: AbstractBlockChain
  def peerGroup: PeerGroup
  def wallet: SmartWallet
  def seedPeers: Seq[NetworkEndpoint]
}

object BitcoinPlatform {
  trait Component {
    def bitcoinPlatform: BitcoinPlatform
  }
}
