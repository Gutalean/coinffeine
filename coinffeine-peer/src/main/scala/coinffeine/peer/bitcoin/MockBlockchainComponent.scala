package coinffeine.peer.bitcoin

import com.google.bitcoin.core.{AbstractBlockChain, FullPrunedBlockChain}
import com.google.bitcoin.store.MemoryFullPrunedBlockStore

import coinffeine.model.bitcoin.{BlockchainComponent, NetworkComponent}

trait MockBlockchainComponent extends BlockchainActor.Component with BlockchainComponent {

  this: NetworkComponent =>

  override lazy val blockchain: AbstractBlockChain = {
    val blockStore = new MemoryFullPrunedBlockStore(network, 1000)
    new FullPrunedBlockChain(network, blockStore)
  }
}
