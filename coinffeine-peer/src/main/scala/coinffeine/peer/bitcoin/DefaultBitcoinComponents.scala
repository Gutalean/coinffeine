package coinffeine.peer.bitcoin

import com.google.bitcoin.core.{FullPrunedBlockChain, PeerGroup, AbstractBlockChain}
import com.google.bitcoin.kits.WalletAppKit
import com.google.bitcoin.store.MemoryFullPrunedBlockStore

import coinffeine.model.bitcoin.{NetworkComponent, BlockchainComponent, PeerGroupComponent}
import coinffeine.peer.config.SettingsProvider
import coinffeine.peer.config.user.LocalAppDataDir

trait DefaultBitcoinComponents extends PeerGroupComponent with BlockchainComponent {

  this: NetworkComponent =>

  override lazy val blockchain: AbstractBlockChain = {
    val blockStore = new MemoryFullPrunedBlockStore(network, 1000)
    new FullPrunedBlockChain(network, blockStore)
  }

  override lazy val peerGroup = {
    val peerGroup = new PeerGroup(network, blockchain)
    peerAddresses.foreach(peerGroup.addAddress)
    peerGroup
  }
}
